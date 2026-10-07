import torch
import torch.nn as nn
import torch.nn.functional as F

# ==========================================
# 1. StripConv (条纹卷积) - 用于捕捉细长血管
# ==========================================
class StripConv(nn.Module):
    def __init__(self, in_ch, out_ch, kernel_size=7):
        super().__init__()
        # 计算 padding 使得输出尺寸不变
        padding = kernel_size // 2

        # 分支 H: 捕捉水平特征 [1, K]
        self.conv_h = nn.Conv2d(in_ch, out_ch, kernel_size=(1, kernel_size),
                                stride=1, padding=(0, padding), bias=False)

        # 分支 V: 捕捉垂直特征 [K, 1]
        self.conv_v = nn.Conv2d(in_ch, out_ch, kernel_size=(kernel_size, 1),
                                stride=1, padding=(padding, 0), bias=False)

        self.bn = nn.BatchNorm2d(out_ch)
        self.relu = nn.ReLU(inplace=True)

    def forward(self, x):
        # 两个方向的特征融合（相加）
        x_h = self.conv_h(x)
        x_v = self.conv_v(x)
        return self.relu(self.bn(x_h + x_v))

# ==========================================
# 2. SK-Fusion (选择性核融合) - 动态分配权重
# ==========================================
class SKFusion(nn.Module):
    def __init__(self, channels, branches=3, reduction=8):
        super().__init__()
        self.branches = branches
        # 压缩维度的中间层，最小不低于 32
        mid_channels = max(channels // reduction, 32)

        # MLP: Squeeze -> ReLU -> Expand
        self.mlp = nn.Sequential(
            nn.AdaptiveAvgPool2d(1),              # Global Avg Pool
            nn.Conv2d(channels, mid_channels, 1),
            nn.ReLU(inplace=True),
            nn.Conv2d(mid_channels, channels * branches, 1) # 输出维度 = 通道 * 分支数
        )
        self.softmax = nn.Softmax(dim=1) # 在分支维度进行归一化

    def forward(self, *feats):
        # feats: list of [B, C, H, W] tensors
        # 1. Stack: [B, Branches, C, H, W]
        U = torch.stack(feats, dim=1)

        # 2. Sum: 获得综合特征 [B, C, H, W]
        S = torch.sum(U, dim=1)

        # 3. Calculate Weights: [B, Branches, C, 1, 1]
        # B, C*Branches, 1, 1
        Z = self.mlp(S)
        # Reshape to allow broadcasting
        weights = Z.view(S.size(0), self.branches, S.size(1), 1, 1)
        weights = self.softmax(weights)

        # 4. Fuse: Weighted Sum
        V = torch.sum(U * weights, dim=1)
        return V

# ==========================================
# 3. Coordinate Branch (保持原有的定位能力)
# ==========================================
class CoordinateBranch(nn.Module):
    def __init__(self, in_ch, mid_ch):
        super().__init__()

        self.conv1 = nn.Conv2d(in_ch, mid_ch, kernel_size=1, bias=False)
        self.bn1 = nn.BatchNorm2d(mid_ch)
        self.act = nn.ReLU(inplace=True)

        self.conv_h = nn.Conv2d(mid_ch, in_ch, kernel_size=1, bias=False)
        self.conv_w = nn.Conv2d(mid_ch, in_ch, kernel_size=1, bias=False)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        identity = x
       # ✅ 优化 2: 使用 mean 代替 AdaptivePool
        identity = x
        # ✅ 优化 2: 使用 mean 代替 AdaptivePool
        x_h = torch.mean(x, dim=3, keepdim=True)
        x_w = torch.mean(x, dim=2, keepdim=True).permute(0, 1, 3, 2)

        y = torch.cat([x_h, x_w], dim=2)
        y = self.act(self.bn1(self.conv1(y)))

        h, w = x.shape[2], x.shape[3]
        x_h, x_w = torch.split(y, [h, w], dim=2)
        x_w = x_w.permute(0, 1, 3, 2)

        a_h = self.sigmoid(self.conv_h(x_h))
        a_w = self.sigmoid(self.conv_w(x_w))
        return identity * a_h * a_w

# ==========================================
# 4. Cross-Spatial Learning (保持原有的交互)
# ==========================================
class CrossSpatialLearning(nn.Module):
    def __init__(self, channels):
        super().__init__()
        self.global_pool = nn.AdaptiveAvgPool2d(1)
        self.channel_map = nn.Sequential(
            nn.Conv2d(channels, channels, 1),
            nn.Sigmoid()
        )
        self.gn = nn.GroupNorm(num_groups=8, num_channels=channels)
    def forward(self, x_strip, x_coord):
        # Path 1: Strip 指导 Coord
        f1_vec = self.global_pool(x_strip)
        f1_att = self.channel_map(f1_vec)  # [B, C, 1, 1], range (0, 1)
        out_1 = x_coord * f1_att

        # Path 2: Coord 指导 Strip
        x_coord_gn = self.gn(x_coord)
        f2_vec = self.global_pool(x_coord_gn)
        f2_att = self.channel_map(f2_vec)  # share weights or define separate?
        # 建议共用或单独定义一个 channel_map2，这里简单起见复用
        out_2 = x_strip * f2_att

        return out_1 + out_2

# ==========================================
# 5. NEW DMC_Block (主模块)
# ==========================================
class SCS_Module(nn.Module):
    def __init__(self, in_ch, out_ch, drop_rate=0.1):
        super().__init__()

        # 中间通道数，通常为输出通道的 1/4 以节省计算量
        mid_ch = out_ch // 4

        # --- A. Strip Sensing Branch (替代原 Dilated Branch) ---
        # 1. 极长条纹 (Long-range): 11x1 & 1x11
        self.strip_long = StripConv(in_ch, mid_ch, kernel_size=11)
        # 2. 中等条纹 (Mid-range): 7x1 & 1x7
        self.strip_mid = StripConv(in_ch, mid_ch, kernel_size=7)
        self.conv_3x3 = nn.Sequential(
            nn.Conv2d(in_ch, mid_ch, 3, padding=1, bias=False),
            nn.BatchNorm2d(mid_ch), nn.ReLU(inplace=True)
        )
        # 3. 局部感知 (Short-range): 1x1
        self.conv_short = nn.Sequential(
            nn.Conv2d(in_ch, mid_ch, 1, bias=False),
            nn.BatchNorm2d(mid_ch), nn.ReLU(inplace=True)
        )

        # --- B. SK-Fusion (动态融合) ---
        # 融合上述 3 个分支，自动决定谁更重要
        self.sk_fusion = SKFusion(mid_ch, branches=4)

        # 融合后投影回 out_ch
        self.fusion_proj = nn.Conv2d(mid_ch, out_ch, 1, bias=False)

        # --- C. Coordinate Branch (定位分支) ---
        self.coord_branch = CoordinateBranch(in_ch, mid_ch)
        self.coord_proj = nn.Conv2d(in_ch, out_ch, 1, bias=False)

        # --- D. Cross Learning & Output ---
        self.cross = CrossSpatialLearning(out_ch)
        self.dropout = nn.Dropout2d(drop_rate)
        # Shortcut connection (处理输入输出通道不一致的情况)
        self.shortcut = nn.Conv2d(in_ch, out_ch, 1) if in_ch != out_ch else nn.Identity()

    def forward(self, x):
        # 1. 获取三种形态的特征
        f_long = self.strip_long(x)   # 长血管特征
        f_mid  = self.strip_mid(x)    # 弯曲血管特征
        f_local = self.conv_3x3(x)
        f_short = self.conv_short(x)  # 局部纹理特征

        # 2. SK-Fusion 动态融合
        # "大脑" 决定当前像素更像长血管还是背景
        f_strip = self.sk_fusion(f_long, f_mid,f_local, f_short)
        f_strip = self.fusion_proj(f_strip) # F1

        # 3. Coordinate Branch 精准定位
        f_coord = self.coord_branch(x)
        f_coord = self.coord_proj(f_coord)  # F2

        # 4. 交互学习与门控
        # F1 和 F2 互相指导
        cross_feat = self.cross(f_strip, f_coord)

        # 5. 最终残差门控
        # 用学到的 Attention Map 过滤原始输入
        out = self.shortcut(x) + cross_feat

        return self.dropout(out)