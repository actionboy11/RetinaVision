import torch
import torch.nn as nn
import torch.nn.functional as F

# ==========================================
# 1. StripConv (条纹卷积) - 用于捕捉细长血管
# ==========================================
class StripConv(nn.Module):
    def __init__(self, in_ch, out_ch, kernel_size=7):
        super().__init__()
        padding = kernel_size // 2

        # 分支 H: 捕捉水平特征 [1, K]
        self.conv_h = nn.Conv2d(
            in_ch, out_ch,
            kernel_size=(1, kernel_size),
            stride=1,
            padding=(0, padding),
            bias=False
        )

        # 分支 V: 捕捉垂直特征 [K, 1]
        self.conv_v = nn.Conv2d(
            in_ch, out_ch,
            kernel_size=(kernel_size, 1),
            stride=1,
            padding=(padding, 0),
            bias=False
        )

        self.bn = nn.BatchNorm2d(out_ch)
        self.relu = nn.ReLU(inplace=True)

    def forward(self, x):
        x_h = self.conv_h(x)
        x_v = self.conv_v(x)
        return self.relu(self.bn(x_h + x_v))


# ==========================================
# 2. SK-Fusion (选择性核融合) - 动态分配权重
# ==========================================
class SKFusion(nn.Module):
    def __init__(self, channels, branches=4, reduction=8):
        super().__init__()
        self.branches = branches
        mid_channels = max(channels // reduction, 32)

        self.mlp = nn.Sequential(
            nn.AdaptiveAvgPool2d(1),
            nn.Conv2d(channels, mid_channels, 1, bias=True),
            nn.ReLU(inplace=True),
            nn.Conv2d(mid_channels, channels * branches, 1, bias=True)
        )
        self.softmax = nn.Softmax(dim=1)

    def forward(self, *feats):
        # feats: multiple [B, C, H, W] tensors
        U = torch.stack(feats, dim=1)       # [B, branches, C, H, W]
        S = torch.sum(U, dim=1)             # [B, C, H, W]

        Z = self.mlp(S)                     # [B, C*branches, 1, 1]
        weights = Z.view(S.size(0), self.branches, S.size(1), 1, 1)
        weights = self.softmax(weights)

        V = torch.sum(U * weights, dim=1)
        return V


# ==========================================
# 3. Directional Morphology Interaction (DMI)
#    替代原 CoordinateBranch + CrossSpatialLearning
# ==========================================
class DirectionalMorphologyInteraction(nn.Module):
    """
    Directional Morphology Interaction (DMI).

    Purpose:
        Use horizontal and vertical depthwise strip convolutions to generate
        a direction-aware gate from strip morphology features, and then
        reweight the original spatial feature.

    Input:
        x       : original/projected input feature, [B, C, H, W]
        f_strip : strip morphology feature, [B, C, H, W]

    Output:
        direction-enhanced feature, [B, C, H, W]
    """
    def __init__(self, channels, kernel_size=7, drop_rate=0.1):
        super().__init__()
        padding = kernel_size // 2

        # Horizontal depthwise strip convolution: 1 x K
        self.h_strip = nn.Conv2d(
            channels,
            channels,
            kernel_size=(1, kernel_size),
            padding=(0, padding),
            groups=channels,
            bias=False
        )

        # Vertical depthwise strip convolution: K x 1
        self.v_strip = nn.Conv2d(
            channels,
            channels,
            kernel_size=(kernel_size, 1),
            padding=(padding, 0),
            groups=channels,
            bias=False
        )

        # Generate direction-aware gate A_dir
        self.gate = nn.Sequential(
            nn.Conv2d(channels, channels, kernel_size=1, bias=False),
            nn.BatchNorm2d(channels),
            nn.Sigmoid()
        )

        # Fuse strip morphology feature and directionally reweighted feature
        self.fuse = nn.Sequential(
            nn.Conv2d(channels * 2, channels, kernel_size=1, bias=False),
            nn.BatchNorm2d(channels),
            nn.ReLU(inplace=True)
        )

        self.dropout = nn.Dropout2d(drop_rate)

    def forward(self, x, f_strip):
        # A_dir = sigmoid(Conv1x1(StripConv1xK(F_strip) + StripConvKx1(F_strip)))
        dir_response = self.h_strip(f_strip) + self.v_strip(f_strip)
        a_dir = self.gate(dir_response)

        # F_dir = X * A_dir
        f_dir = x * a_dir

        # F_DMI = Conv1x1([F_strip, F_dir])
        f_dmi = self.fuse(torch.cat([f_strip, f_dir], dim=1))

        # Residual output
        return self.dropout(f_dmi + x)


# ==========================================
# 4. SCS_Module / DSAF 主模块
#    当前版本：多尺度条纹感知 + SK融合 + DMI方向形态交互
# ==========================================
class SCS_Module(nn.Module):
    """
    Directional Strip-Aware Fusion (DSAF) implementation.

    Compared with the previous version, this version removes:
        1) CoordinateBranch based on X/Y directional pooling
        2) CrossSpatialLearning

    It uses DirectionalMorphologyInteraction (DMI) instead.
    """
    def __init__(self, in_ch, out_ch, drop_rate=0.1, dmi_kernel_size=7):
        super().__init__()

        mid_ch = out_ch // 4

        # A. Multi-scale strip sensing branches
        self.strip_long = StripConv(in_ch, mid_ch, kernel_size=11)
        self.strip_mid = StripConv(in_ch, mid_ch, kernel_size=7)
        self.conv_3x3 = nn.Sequential(
            nn.Conv2d(in_ch, mid_ch, 3, padding=1, bias=False),
            nn.BatchNorm2d(mid_ch),
            nn.ReLU(inplace=True)
        )
        self.conv_short = nn.Sequential(
            nn.Conv2d(in_ch, mid_ch, 1, bias=False),
            nn.BatchNorm2d(mid_ch),
            nn.ReLU(inplace=True)
        )

        # B. SK-Fusion dynamically fuses different strip/local branches
        self.sk_fusion = SKFusion(mid_ch, branches=4)

        # Project strip morphology feature to out_ch
        self.fusion_proj = nn.Sequential(
            nn.Conv2d(mid_ch, out_ch, 1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True)
        )

        # Project original input to out_ch, so DMI can use x and f_strip with same channels
        self.input_proj = nn.Sequential(
            nn.Conv2d(in_ch, out_ch, 1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True)
        ) if in_ch != out_ch else nn.Identity()

        # C. Directional Morphology Interaction
        self.dmi = DirectionalMorphologyInteraction(
            channels=out_ch,
            kernel_size=dmi_kernel_size,
            drop_rate=drop_rate
        )

        # Shortcut connection for final residual matching
        self.shortcut = nn.Conv2d(in_ch, out_ch, 1, bias=False) if in_ch != out_ch else nn.Identity()

    def forward(self, x):
        # 1. Multi-scale strip/local feature extraction
        f_long = self.strip_long(x)
        f_mid = self.strip_mid(x)
        f_local = self.conv_3x3(x)
        f_short = self.conv_short(x)

        # 2. Selective fusion to obtain strip morphology feature
        f_strip = self.sk_fusion(f_long, f_mid, f_local, f_short)
        f_strip = self.fusion_proj(f_strip)

        # 3. Project original feature to the same channel dimension
        x_proj = self.input_proj(x)

        # 4. Directional morphology interaction
        f_dmi = self.dmi(x_proj, f_strip)

        # 5. Final residual output
        out = f_dmi + self.shortcut(x)
        return out
