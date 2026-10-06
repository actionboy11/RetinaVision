import torch
import torch.nn as nn
import torch.nn.functional as F
from .DS import StripConv


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

# =================================================================
# ✅ 新增：C-CGA-Fusion (用于浅层 d1)
# =================================================================
class SpatialAttention(nn.Module):
    """
    Spatial Attention Module
    对应图中上方灰色区域：
    Input -> [MaxPool, AvgPool] (Channel-wise) -> Concat -> Conv -> Sigmoid
    """
    def __init__(self, kernel_size=7):
        super(SpatialAttention, self).__init__()
        assert kernel_size in (3, 7), 'kernel size must be 3 or 7'
        padding = 3 if kernel_size == 7 else 1

        # 输入通道为2，因为拼接了 MaxPool 和 AvgPool 的结果
        self.conv1 = nn.Conv2d(2, 1, kernel_size, padding=padding, bias=False)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        # 沿通道维度进行 AvgPool 和 MaxPool
        avg_out = torch.mean(x, dim=1, keepdim=True)
        max_out, _ = torch.max(x, dim=1, keepdim=True)

        # 拼接 [B, 2, H, W]
        x_cat = torch.cat([avg_out, max_out], dim=1)

        # 卷积 + Sigmoid 生成空间权重 [B, 1, H, W]
        x_out = self.conv1(x_cat)
        return self.sigmoid(x_out)

class ChannelAttention(nn.Module):
    """
    Channel Attention Module
    对应图中左下蓝色区域：
    Input -> Global [MaxPool, AvgPool] -> Shared MLP -> Sum -> Sigmoid
    """
    def __init__(self, in_planes, ratio=16):
        super(ChannelAttention, self).__init__()
        # 共享 MLP
        self.avg_pool = nn.AdaptiveAvgPool2d(1)
        self.max_pool = nn.AdaptiveMaxPool2d(1)

        # MLP 结构: Linear -> ReLU -> Linear
        self.fc1 = nn.Conv2d(in_planes, in_planes // ratio, 1, bias=False)
        self.relu1 = nn.ReLU()
        self.fc2 = nn.Conv2d(in_planes // ratio, in_planes, 1, bias=False)

        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        # Global Avg Pool [B, C, 1, 1] -> MLP
        avg_out = self.fc2(self.relu1(self.fc1(self.avg_pool(x))))

        # Global Max Pool [B, C, 1, 1] -> MLP
        max_out = self.fc2(self.relu1(self.fc1(self.max_pool(x))))

        # Element-wise Summation + Sigmoid
        out = avg_out + max_out
        return self.sigmoid(out)

class S_CGA_Fusion(nn.Module):
    """
    S-CGA (Strip-Content Guided Attention) Fusion
    创新点：将原版 CGA 中的 Pixel Attention 替换为 Strip Attention，
    专门用于生成贴合血管走向的融合权重。
    """
    def __init__(self, channels, ratio=16):
        super(S_CGA_Fusion, self).__init__()

        # 1. Spatial Attention (保持不变，用于定位血管区域)
        self.spatial_att = SpatialAttention(kernel_size=7)

        # 2. Channel Attention (保持不变，用于筛选特征通道)
        self.channel_att = ChannelAttention(channels, ratio=ratio)

        # 3. ✅【核心创新】Pixel Attention -> Strip Attention
        # 原版是 3x3 Group Conv，现在换成 Strip Conv (1x7 + 7x1)
        # 这样生成的权重图 W 会呈现条纹状，精准覆盖血管
        self.strip_conv = StripConv(channels, channels, kernel_size=7)

        # 最后的 Pointwise 调整，将多通道压缩或调整
        self.pointwise = nn.Conv2d(channels, channels, kernel_size=1, bias=False)

        self.sigmoid = nn.Sigmoid()

    def forward(self, encoder_feat, decoder_feat):
        # 0. 初始融合：简单相加作为注意力的输入
        f_sum = encoder_feat + decoder_feat

        # 1. 获取空间注意力 [B, 1, H, W]
        sa_map = self.spatial_att(f_sum)

        # 2. 获取通道注意力 [B, C, 1, 1]
        ca_vec = self.channel_att(f_sum)

        # 3. 混合特征：广播相加
        # 此时 pixel_in 包含了空间位置信息和通道重要性信息
        pixel_in = f_sum + sa_map + ca_vec

        # 4. ✅【创新】生成“条纹感知”的融合权重 W
        # W 的形状是 [B, C, H, W]，每一个像素都有一个独立的融合比例
        w = self.strip_conv(pixel_in)
        w = self.pointwise(w)
        w = self.sigmoid(w)

        # 5. 加权融合 (Gated Fusion)
        # W 趋近 1 -> 信任 Decoder (深层语义)
        # W 趋近 0 -> 信任 Encoder (浅层细节)
        out_enc = encoder_feat * (1 - w)
        out_dec = decoder_feat * w

        out = out_enc + out_dec

        return out

# =================================================================
# 2. R-SGA Block (ResPath + Strip Guided Attention Fusion)
# 适用位置：浅层 (Stage 1)
# =================================================================
class R_SGA_Block(nn.Module):
    def __init__(self, enc_ch, dec_ch, out_ch, res_length=3):
        """
        Args:
            enc_ch: 编码器输出的原始通道数 (e.g., 64)
            dec_ch: 解码器上采样后的通道数 (e.g., 64)
            out_ch: 融合后的输出通道数 (e.g., 64)
            res_length: ResPath 的深度 (浅层语义差距大，通常较长, e.g., 3)
        """
        super().__init__()

        # 1. 内部集成 ResPath
        self.respath = ResPath(in_ch=enc_ch, out_ch=enc_ch, length=res_length)

        # 2. 内部集成 S-CGA (Strip CGA)
        # 注意：CGA 通常期望输入通道数一致，或者在内部处理。
        # 这里的 S_CGA_Fusion 初始化参数 channels 通常指的是期望的统一通道数
        self.fusion = S_CGA_Fusion(channels=out_ch)

        # 如果 enc_ch 或 dec_ch 与 out_ch 不一致，需要 1x1 卷积对齐
        self.align_enc = nn.Conv2d(enc_ch, out_ch, 1) if enc_ch != out_ch else nn.Identity()
        self.align_dec = nn.Conv2d(dec_ch, out_ch, 1) if dec_ch != out_ch else nn.Identity()

    def forward(self, enc, dec):
        """
        输入:
            enc: 来自编码器的原始特征
            dec: 来自解码器的上采样特征
        """
        # Step 1: Encoder 特征先过 ResPath
        enc_processed = self.respath(enc)

        # Step 2: 通道对齐 (为了适配 CGA 的输入要求)
        enc_aligned = self.align_enc(enc_processed)
        dec_aligned = self.align_dec(dec)

        # Step 3: Strip-CGA 融合
        out = self.fusion(encoder_feat=enc_aligned, decoder_feat=dec_aligned)

        return out


class CGA_Fusion(nn.Module):
    """
    Content-Guided Attention Fusion (CGA-Fusion) [cite: 1021]
    包含三个部分：Spatial, Channel, Pixel Attention。
    融合逻辑：Encoder 和 Decoder 特征首先相加，经过注意力模块生成权重 W，
    最后输出 (1-W)*Encoder + W*Decoder。
    """
    def __init__(self, channels, ratio=16, group_kernel=3):
        super(CGA_Fusion, self).__init__()

        # 1. Spatial Attention Module
        self.spatial_att = SpatialAttention(kernel_size=7)

        # 2. Channel Attention Module
        self.channel_att = ChannelAttention(channels, ratio=ratio)

        # 3. Pixel Attention Module (Group Conv) [cite: 1016]
        # 对应图中右侧灰色区域：输入为 F_sum + Spatial + Channel
        # 使用 Group Conv 进行特征细化
        self.group_conv = nn.Sequential(
            nn.Conv2d(channels, channels, kernel_size=group_kernel,
                      padding=group_kernel//2, groups=channels, bias=False), # Depthwise Conv
            nn.BatchNorm2d(channels),
            nn.ReLU(inplace=True), # 图中未明确画出ReLU，但通常卷积后会有激活，也可去掉
            nn.Conv2d(channels, channels, kernel_size=1, bias=False) # Pointwise 调整
        )
        self.sigmoid = nn.Sigmoid()

    def forward(self, encoder_feat, decoder_feat):
        """
        encoder_feat: 来自编码器的低级特征 [B, C, H, W]
        decoder_feat: 来自解码器的高级特征 [B, C, H, W]
        """
        # 0. 初始融合：Element-wise summation
        # 对应图中最左侧的加号
        f_sum = encoder_feat + decoder_feat

        # 1. 获取空间注意力 [B, 1, H, W]
        sa_map = self.spatial_att(f_sum)

        # 2. 获取通道注意力 [B, C, 1, 1]
        ca_vec = self.channel_att(f_sum)

        # 3. Pixel Attention Module 处理
        # 对应图中中间的加号逻辑：F_sum + Spatial + Channel
        # 注意：利用广播机制 (Broadcasting) 将 (B,1,H,W) 和 (B,C,1,1) 加到 (B,C,H,W) 上
        pixel_in = f_sum + sa_map + ca_vec

        # Group Conv + Sigmoid 生成最终权重 W [cite: 1019]
        # 对应图中 "Group Conv" -> "Sigmoid" -> W
        w = self.sigmoid(self.group_conv(pixel_in))

        # 4. 最终加权融合
        # 对应图中最右侧逻辑：(1-W) * Encoder + W * Decoder
        # Top path: Encoder * (1-W) [cite: 1009]
        out_enc = encoder_feat * (1 - w)

        # Bottom path: Decoder * W [cite: 1019]
        out_dec = decoder_feat * w

        # Element-wise summation
        out = out_enc + out_dec

        return out
