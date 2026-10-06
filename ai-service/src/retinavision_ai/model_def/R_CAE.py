import torch
import torch.nn as nn
import torch.nn.functional as F
import os

# ----------------- ResPath 模块 -----------------
class ResByte(nn.Module):
    """
    ResPath 的基本构建块 (即一个残差块)
    结构: Input -> [Conv-BN-ReLU-Conv-BN-ReLU] + Input -> Output
    """
    def __init__(self, in_ch, out_ch):
        super().__init__()
        self.proj = nn.Identity()
        # 如果输入输出通道不一致，需要 1x1 卷积调整 shortcut
        if in_ch != out_ch:
            self.proj = nn.Sequential(
                nn.Conv2d(in_ch, out_ch, 1, bias=False),
                nn.BatchNorm2d(out_ch)
            )

        self.body = nn.Sequential(
            nn.Conv2d(in_ch, out_ch, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True),
            nn.Conv2d(out_ch, out_ch, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        return self.body(x) + self.proj(x)

class ResPath(nn.Module):
    """
    ResPath 路径
    length: 路径长度 (包含几个残差块)。浅层路径长，深层路径短。
    """
    def __init__(self, in_ch, out_ch, length):
        super().__init__()
        layers = []
        for i in range(length):
            # 第一个块处理通道变换，后续块保持通道不变
            c_in = in_ch if i == 0 else out_ch
            layers.append(ResByte(c_in, out_ch))
        self.blocks = nn.Sequential(*layers)

    def forward(self, x):
        return self.blocks(x)

class CAE_Fusion_Block(nn.Module):
    """
    CAE-Fusion (Clean Adaptive Edge Fusion)
    创新点：
    1. 修正了 AE-Fusion 的 Softmax 逻辑错误，改用 Sigmoid 门控。
    2. 引入瓶颈层减少参数冗余。
    3. 更加干净的边缘保留机制。
    """
    def __init__(self, enc_ch, dec_ch, out_ch):
        super().__init__()

        # 1. 维度对齐
        self.conv_enc = nn.Conv2d(enc_ch, out_ch, 1, bias=False)
        self.conv_dec = nn.Conv2d(dec_ch, out_ch, 1, bias=False)

        # 2. ✅【改进】Attention 生成器 (Bottleneck 结构)
        # 相比原版直接 Conv -> Softmax，这里更深但参数更少
        self.att_gate = nn.Sequential(
            nn.Conv2d(out_ch * 2, out_ch // 2, 1), # 降维 (Bottleneck)
            nn.BatchNorm2d(out_ch // 2),
            nn.ReLU(inplace=True),
            nn.Conv2d(out_ch // 2, 1, 1),          # 映射到 1 通道
            nn.Sigmoid()                           # 生成 0~1 的权重图
        )

        # 3. 边缘提取 (Edge Preservation)
        # 保留 MaxPool/AvgPool 逻辑，这是提取高频信息的有效手段
        self.max_pool = nn.MaxPool2d(kernel_size=3, stride=1, padding=1)
        self.avg_pool = nn.AvgPool2d(kernel_size=3, stride=1, padding=1)

        self.sigmoid = nn.Sigmoid()

        # 4. 最终融合
        self.final_conv = nn.Sequential(
            nn.Conv2d(out_ch + 1, out_ch, 1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True)
        )

    def forward(self, enc, dec):
        # Step 1: 对齐维度
        enc_feat = self.conv_enc(enc)
        dec_feat = self.conv_dec(dec)

        # Step 2: 生成注意力并重加权 Encoder
        # 我们希望 Encoder 只把“有用的细节”传给 Decoder，而不是把噪声也传过去
        combined = torch.cat([dec_feat, enc_feat], dim=1)
        gate_map = self.att_gate(combined) # [B, 1, H, W]

        # 只增强 Encoder 中被 Decoder 认为是重要的区域
        f_weighted = enc_feat * gate_map

        # Step 3: 边缘增强
        # 将 Decoder 特征与“干净的”Encoder 特征结合
        f_b = torch.cat([dec_feat, f_weighted], dim=1)

        # 提取高频边缘信息 (模拟形态学梯度)
        edge_feat = self.max_pool(f_b) + self.avg_pool(f_b)
        edge_feat = torch.mean(edge_feat, dim=1, keepdim=True)
        edge_feat = self.sigmoid(edge_feat) # 得到边缘图

        # Step 4: 最终融合 (Feature + Edge)
        # 将边缘作为显式的特征通道补充进去
        out = torch.cat([f_weighted, edge_feat], dim=1)

        return self.final_conv(out)

    # =================================================================
# 1. R-CAE Block (ResPath + Clean Adaptive Edge Fusion)
# 适用位置：深层 (Stage 2)
# =================================================================
class R_CAE_Block(nn.Module):
    def __init__(self, enc_ch, dec_ch, out_ch, res_length=2):
        """
        Args:
            enc_ch: 编码器输出的原始通道数 (e.g., 128)
            dec_ch: 解码器上采样后的通道数 (e.g., 64)
            out_ch: 融合后的输出通道数 (e.g., 64)
            res_length: ResPath 的深度 (深层通常较短, e.g., 2)
        """
        super().__init__()

        # 1. 内部集成 ResPath
        # 作用：处理来自 Encoder 的 Skip Connection，减少语义鸿沟
        self.respath = ResPath(in_ch=enc_ch, out_ch=enc_ch, length=res_length)

        # 2. 内部集成 CAE-Fusion (Clean AE)
        # 作用：融合处理后的 Encoder 特征和 Decoder 特征
        self.fusion = CAE_Fusion_Block(enc_ch=enc_ch, dec_ch=dec_ch, out_ch=out_ch)

    def forward(self, enc, dec):
        """
        输入:
            enc: 来自编码器的原始特征 [B, enc_ch, H, W]
            dec: 来自解码器的上采样特征 [B, dec_ch, H, W]
        """
        # Step 1: Encoder 特征先过 ResPath
        enc_processed = self.respath(enc)

        # Step 2: 融合 (注意 CAE 内部会自动处理通道对齐)
        out = self.fusion(enc=enc_processed, dec=dec)

        return out