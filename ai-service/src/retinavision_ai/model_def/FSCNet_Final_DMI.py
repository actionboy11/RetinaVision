import torch
import torch.nn as nn
import torch.nn.functional as F

from .CT_SPM import CT_SPM
from .DS_DMI import SCS_Module
from .R_CAE import R_CAE_Block
from .R_SGA import R_SGA_Block

# ==========================================
# Frequency-Enhanced Attention, FEA
# ==========================================
class ImprovedFrequencyFilter(nn.Module):
    """
    Learnable low/mid/high frequency filtering with bounded residual injection.
    """
    def __init__(self, channels):
        super().__init__()
        self.low_w = nn.Parameter(torch.zeros(1, channels, 1, 1))
        self.mid_w = nn.Parameter(torch.zeros(1, channels, 1, 1))
        self.high_w = nn.Parameter(torch.zeros(1, channels, 1, 1))
        self.res_scale = nn.Parameter(torch.ones(1, channels, 1, 1) * 0.1)

        self.cached_size = None
        self.cached_masks = None

    def get_masks(self, h, w, device):
        if self.cached_size == (h, w) and self.cached_masks is not None:
            return self.cached_masks

        u = torch.linspace(-0.5, 0.5, h, device=device).reshape(h, 1)
        v = torch.linspace(-0.5, 0.5, w, device=device).reshape(1, w)
        d = torch.sqrt(u * u + v * v)

        low_mask = torch.exp(-(d * 10) ** 2)
        mid_mask = torch.exp(-((d - 0.25) * 12) ** 2)
        high_mask = torch.exp(-((d - 0.45) * 15) ** 2)

        self.cached_size = (h, w)
        self.cached_masks = (low_mask, mid_mask, high_mask)
        return self.cached_masks

    def forward(self, x):
        _, _, h, w = x.shape

        low_mask, mid_mask, high_mask = self.get_masks(h, w, x.device)

        x_fft = torch.fft.rfft2(x, s=(h, w), norm="ortho")
        rfft_w = x_fft.shape[-1]

        low = F.softplus(self.low_w) * low_mask[..., :rfft_w]
        mid = F.softplus(self.mid_w) * mid_mask[..., :rfft_w]
        high = F.softplus(self.high_w) * high_mask[..., :rfft_w]

        freq_filter = low + mid + high
        x_freq = torch.fft.irfft2(x_fft * freq_filter, s=(h, w), norm="ortho")

        scale = torch.clamp(self.res_scale, 0.0, 0.5)
        return x + scale * x_freq


class CBAM(nn.Module):
    """Lightweight channel-spatial recalibration."""
    def __init__(self, channels, reduction=8):
        super().__init__()
        hidden = max(channels // reduction, 1)

        self.mlp = nn.Sequential(
            nn.Conv2d(channels, hidden, kernel_size=1, bias=False),
            nn.ReLU(inplace=True),
            nn.Conv2d(hidden, channels, kernel_size=1, bias=False),
        )

        self.spatial = nn.Sequential(
            nn.Conv2d(2, 1, kernel_size=7, padding=3, bias=False),
            nn.Sigmoid(),
        )

    def forward(self, x):
        avg = torch.mean(x, dim=(2, 3), keepdim=True)
        maxv = torch.amax(x, dim=(2, 3), keepdim=True)
        ca = torch.sigmoid(self.mlp(avg) + self.mlp(maxv))
        x = x * ca

        avg = torch.mean(x, dim=1, keepdim=True)
        maxv = torch.max(x, dim=1, keepdim=True).values
        sa = self.spatial(torch.cat([avg, maxv], dim=1))
        return x * sa


class FEA_Block(nn.Module):
    """Frequency filter + CBAM recalibration."""
    def __init__(self, channels):
        super().__init__()
        self.freq = ImprovedFrequencyFilter(channels)
        self.cbam = CBAM(channels)

    def forward(self, x):
        return self.cbam(self.freq(x))


# ==========================================
# Shared spatial-frequency encoder block
# ==========================================
class SpatialFrequencyEncoderBlock(nn.Module):
    """
    Shared Conv Stem -> DSAF(DMI) || FEA -> Concat + 1x1 fusion.
    """
    def __init__(self, in_ch, out_ch, drop_rate=0.1):
        super().__init__()

        self.stem = nn.Sequential(
            nn.Conv2d(in_ch, out_ch, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True),
            nn.Dropout2d(drop_rate),
        )

        self.dsaf = SCS_Module(out_ch, out_ch, drop_rate=drop_rate)
        self.fea = FEA_Block(out_ch)

        self.parallel_fusion = nn.Sequential(
            nn.Conv2d(out_ch * 2, out_ch, kernel_size=1, bias=False),
            nn.BatchNorm2d(out_ch),
            nn.ReLU(inplace=True),
        )

    def forward(self, x):
        x = self.stem(x)
        x_dsaf = self.dsaf(x)
        x_fea = self.fea(x)
        return self.parallel_fusion(torch.cat([x_dsaf, x_fea], dim=1))


# ==========================================
# Conv Refinement Block
# ==========================================
class ConvRefinementBlock(nn.Module):
    def __init__(self, channels, drop_rate=0.1, use_dropout=True):
        super().__init__()

        layers = [
            nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(channels),
            nn.ReLU(inplace=True),
        ]

        if use_dropout:
            layers.append(nn.Dropout2d(drop_rate))

        layers.extend([
            nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(channels),
            nn.ReLU(inplace=True),
        ])

        self.block = nn.Sequential(*layers)

    def forward(self, x):
        return self.block(x)


# ==========================================
# Final FSC-Net with DSAF-DMI + FEA parallel encoder
# ==========================================
class FSCNet_Final_DMI(nn.Module):
    def __init__(self, in_ch=1, start_ch=64, drop_rate=0.1):
        super().__init__()

        self.enc1 = SpatialFrequencyEncoderBlock(in_ch, start_ch, drop_rate=drop_rate)
        self.enc2 = SpatialFrequencyEncoderBlock(start_ch, start_ch * 2, drop_rate=drop_rate)
        self.enc3 = SpatialFrequencyEncoderBlock(start_ch * 2, start_ch * 4, drop_rate=drop_rate)

        self.bottleneck_fusion = CT_SPM(
            in_channels_list=[start_ch, start_ch * 2, start_ch * 4],
            embed_dim=128,
            fixed_size=(12, 12),
        )

        self.bridge = ConvRefinementBlock(start_ch * 4, drop_rate=0.1, use_dropout=True)

        self.up_d2 = nn.ConvTranspose2d(
            start_ch * 4,
            start_ch * 2,
            kernel_size=3,
            stride=2,
            padding=1,
            output_padding=1,
        )

        self.deep_rcae = R_CAE_Block(
            enc_ch=start_ch * 2,
            dec_ch=start_ch * 2,
            out_ch=start_ch * 2,
            res_length=2,
        )

        self.dec2_conv = ConvRefinementBlock(start_ch * 2, drop_rate=drop_rate, use_dropout=False)

        self.up_d1 = nn.ConvTranspose2d(
            start_ch * 2,
            start_ch,
            kernel_size=3,
            stride=2,
            padding=1,
            output_padding=1,
        )

        self.shallow_rsga = R_SGA_Block(
            enc_ch=start_ch,
            dec_ch=start_ch,
            out_ch=start_ch,
            res_length=3,
        )

        self.conv_d1 = ConvRefinementBlock(start_ch, drop_rate=drop_rate, use_dropout=True)

        self.final_conv = nn.Sequential(
            nn.Conv2d(start_ch, start_ch, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(start_ch),
            nn.ReLU(inplace=True),
            nn.Conv2d(start_ch, 1, kernel_size=1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        reshape_needed = False

        if x.dim() == 5:
            b_orig, n_orig, c_orig, h_orig, w_orig = x.shape
            x = x.view(b_orig * n_orig, c_orig, h_orig, w_orig)
            reshape_needed = True

        # Encoder: each output is a DSAF(DMI) || FEA fused spatial-frequency feature.
        e1_feat = self.enc1(x)
        p1 = F.max_pool2d(e1_feat, kernel_size=2)

        e2_feat = self.enc2(p1)
        p2 = F.max_pool2d(e2_feat, kernel_size=2)

        e3_feat = self.enc3(p2)

        # GCT-GIM / CT-SPM: global context modeling.
        e1_glob, e2_glob, e3_glob = self.bottleneck_fusion([e1_feat, e2_feat, e3_feat])

        # Context-enhanced skip features.
        skip2_src = e2_feat + e2_glob
        skip1_src = e1_feat + e1_glob

        # Bridge and deep decoder initialization.
        b_bridge = self.bridge(e3_glob)

        d2_up = self.up_d2(b_bridge)
        if d2_up.shape[-2:] != skip2_src.shape[-2:]:
            d2_up = F.interpolate(
                d2_up,
                size=skip2_src.shape[-2:],
                mode="bilinear",
                align_corners=False,
            )

        # Deep stage: R-CAE semantic purification.
        d2_fused = self.deep_rcae(enc=skip2_src, dec=d2_up)
        d2 = self.dec2_conv(d2_fused)

        # Shallow decoder initialization.
        d1_up = self.up_d1(d2)
        if d1_up.shape[-2:] != skip1_src.shape[-2:]:
            d1_up = F.interpolate(
                d1_up,
                size=skip1_src.shape[-2:],
                mode="bilinear",
                align_corners=False,
            )

        # Shallow stage: R-SGA structure-guided refinement.
        d1_fused = self.shallow_rsga(enc=skip1_src, dec=d1_up)
        d1 = self.conv_d1(d1_fused)

        # Prediction head. Raw logits are returned.
        main_out = self.final_conv(d1)

        if reshape_needed:
            main_out = main_out.view(b_orig, n_orig, 1, h_orig, w_orig)

        return main_out


# Backward-compatible aliases for older training scripts.
SA_UNet_improved = FSCNet_Final_DMI
FSCNet = FSCNet_Final_DMI


if __name__ == "__main__":
    # Minimal shape test. External dependencies CT_SPM, R_CAE, and R_SGA must be available.
    model = FSCNet_Final_DMI(in_ch=1, start_ch=64)
    x = torch.randn(2, 1, 96, 96)
    y = model(x)
    print("Input :", x.shape)
    print("Output:", y.shape)
