package com.fongmi.android.tv.player.effect.video;

import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.ColorToneAdjustEffect;
import androidx.media3.effect.DetailAdjustEffect;
import androidx.media3.effect.VideoAdjustment;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

public final class ExoVideoEffectController {

    private final ColorToneAdjustEffect colorTone;
    private final DetailAdjustEffect detail;
    private final List<Effect> effects;
    private boolean configured;

    public ExoVideoEffectController() {
        this.colorTone = new ColorToneAdjustEffect();
        this.detail = new DetailAdjustEffect();
        this.effects = List.of(colorTone, detail);
    }

    public void apply(ExoPlayer player, VideoEffectProfile profile) {
        if (profile.isNoOp()) {
            clear(player);
            return;
        }
        VideoAdjustment adjustment = profile.toAdjustment();
        colorTone.setAdjustment(adjustment);
        detail.setAdjustment(adjustment);
        if (!configured) {
            try {
                player.setVideoEffects(effects);
            } catch (Exception ignored) {
                // media3 effect module is not included in this build; video effects are unavailable
                return;
            }
            configured = true;
        } else if (!player.getPlayWhenReady()) {
            try {
                player.setVideoEffects(VideoFrameProcessor.REDRAW);
            } catch (Exception ignored) {
                // media3 effect module is not included in this build; video effects are unavailable
            }
        }
    }

    public void clear(ExoPlayer player) {
        if (!configured) return;
        try {
            player.setVideoEffects(List.of());
        } catch (Exception ignored) {
            // media3 effect module is not included in this build; video effects are unavailable
        }
        configured = false;
    }
}
