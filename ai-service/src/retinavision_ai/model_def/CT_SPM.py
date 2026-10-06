import torch
import torch.nn as nn
import torch.nn.functional as F

# ==========================================
# 1. 注入器：解决"插值模糊"的关键模块
class GlobalContextTransformer(nn.Module):
    """
    全局上下文变换器 (Global Context Transformer)
    作用：替代原论文中的 Mamba 模块，用于在压缩后的特征图 (12x12) 上进行全局建模。
    结构：标准的 Transformer Encoder Block (LayerNorm -> Self-Attention -> LayerNorm -> MLP)
    """
    def __init__(self, dim, num_heads=4, mlp_ratio=4., drop=0.1):
        super().__init__()
        # 1. 注意力层
        self.norm1 = nn.LayerNorm(dim)
        self.attn = nn.MultiheadAttention(dim, num_heads, dropout=drop, batch_first=True)

        # 2. 前馈网络 (FFN / MLP)
        self.norm2 = nn.LayerNorm(dim)
        self.mlp = nn.Sequential(
            nn.Linear(dim, int(dim * mlp_ratio)),
            nn.GELU(),
            nn.Dropout(drop),
            nn.Linear(int(dim * mlp_ratio), dim),
            nn.Dropout(drop)
        )

    def forward(self, x):
        # x shape: [Batch, Seq_Len, Dim]
        # 例如: [B, 144, 128] (对应 12x12 的图)

        # --- Block 1: Self-Attention + Residual ---
        x_norm = self.norm1(x)
        attn_out, _ = self.attn(x_norm, x_norm, x_norm)
        x = x + attn_out # 残差连接

        # --- Block 2: MLP + Residual ---
        x_norm2 = self.norm2(x)
        mlp_out = self.mlp(x_norm2)
        x = x + mlp_out  # 残差连接

        return x
# ==========================================
class GlobalInjector(nn.Module):
    """
    全局注入器：
    不直接使用模糊的全局特征作为输出，而是用它来'重新加权'清晰的局部特征。
    """
    def __init__(self, channels):
        super().__init__()
        # 用卷积来平滑上采样带来的伪影
        self.gate_conv = nn.Sequential(
            nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(channels),
            nn.Sigmoid() # 生成 0-1 的权重图
        )
        # 可选：加上一个通道注意力，进一步增强
        self.gamma = nn.Parameter(torch.zeros(1)) # 初始为0，让网络自己学习注入多少全局信息

    def forward(self, local_feat, global_feat):
        """
        local_feat: [B, C, 96, 96] (高分辨率，清晰)
        global_feat: [B, C, 12, 12] (低分辨率，全局)
        """
        # 1. 空间对齐：将全局特征上采样回局部尺寸
        # 使用 bilinear 插值，虽然结果是模糊的，但我们只把它当权重用
        global_up = F.interpolate(global_feat, size=local_feat.shape[2:], mode='bilinear', align_corners=False)

        # 2. 生成门控：将全局特征转化为注意力图
        # 这张图代表了"全局上下文认为哪里重要"
        gate = self.gate_conv(global_up)

        # 3. 注入：原始特征 + (原始特征 * 全局门控)
        # 这样保留了 local_feat 的所有高频细节 (边缘)，只是调整了亮度/强度
        out = local_feat + self.gamma * (local_feat * gate)

        return out

# ==========================================
# 2. 改进后的 CT_SPM
# ==========================================
class CT_SPM(nn.Module):
    def __init__(self, in_channels_list, embed_dim=96, fixed_size=(12, 12)):
        super().__init__()
        self.fixed_size = fixed_size

        # 1. 投影层 (调整通道数到 embed_dim)
        self.projections = nn.ModuleList([
            nn.Sequential(
                nn.Conv2d(in_ch, embed_dim, 1, bias=False),
                nn.BatchNorm2d(embed_dim),
                nn.ReLU(inplace=True)
            ) for in_ch in in_channels_list
        ])

        # 2. 全局建模 (Transformer)
        self.global_transformer = GlobalContextTransformer(embed_dim, num_heads=4)

        # 3. 反投影 (还原通道数)
        self.reprojects = nn.ModuleList([
            nn.Sequential(
                nn.Conv2d(embed_dim, in_ch, 1, bias=False),
                nn.BatchNorm2d(in_ch),
                # 这里去掉ReLU，保持特征的原始分布
            ) for in_ch in in_channels_list
        ])

        # 4. 注入器 (核心修改点)
        self.injectors = nn.ModuleList([
            GlobalInjector(in_ch) for in_ch in in_channels_list
        ])

    def forward(self, inputs):
        # inputs: [e1, e2, e3] 原始编码器特征

        # --- Step 1: 统一维度 ---
        projected_feats = [proj(x) for proj, x in zip(self.projections, inputs)]

        # --- Step 2: 聚合 (Pool -> Add) ---
        aggregated_feat = 0
        for feat in projected_feats:
            feat_small = F.adaptive_avg_pool2d(feat, self.fixed_size) # 变成 12x12
            aggregated_feat = aggregated_feat + feat_small

        # --- Step 3: Transformer 全局建模 ---
        B, C, H, W = aggregated_feat.shape
        flatten_feat = aggregated_feat.flatten(2).transpose(1, 2)
        global_context = self.global_transformer(flatten_feat)
        global_context = global_context.transpose(1, 2).view(B, C, H, W) # [B, 128, 12, 12]

        # --- Step 4: 分发与注入 ---
        outputs = []
        for i, raw_input in enumerate(inputs):
            # A. 将 12x12 的全局特征映射回该层的原始通道数 (如 128 -> 64)
            # 注意：这里还是 12x12 的尺寸
            global_feat_channel_aligned = self.reprojects[i](global_context)

            # B. 使用注入器融合 (96x96 与 12x12 交互)
            # 这一步保证了原始 raw_input 的细节不被破坏
            out = self.injectors[i](raw_input, global_feat_channel_aligned)
            outputs.append(out)

        return outputs