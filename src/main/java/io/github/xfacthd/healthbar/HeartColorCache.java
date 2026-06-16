package io.github.xfacthd.healthbar;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.ARGB;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class HeartColorCache implements PreparableReloadListener {
    public static final Identifier ID = HealthBar.id("heart_colors");
    public static final HeartColorCache INSTANCE = new HeartColorCache();
    private static final HeartColor WHITE = new HeartColor(0xFFFFFFFF, 0xFFFFFFFF);

    private Map<Hud.HeartType, HeartColor> colors = Map.of();

    private HeartColorCache() { }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor asyncExecutor, PreparationBarrier barrier, Executor syncExecutor) {
        return sharedState.get(AtlasManager.PENDING_STITCH)
                .get(AtlasIds.GUI)
                .thenApplyAsync(HeartColorCache::prepare, asyncExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply);
    }

    private static Map<Hud.HeartType, HeartColor> prepare(SpriteLoader.Preparations guiAtlas) {
        Map<Hud.HeartType, HeartColor> data = new EnumMap<>(Hud.HeartType.class);
        for (Hud.HeartType type : Hud.HeartType.values()) {
            if (type == Hud.HeartType.CONTAINER) {
                continue;
            }

            int normal = extractColor(guiAtlas, type, false);
            int blink = extractColor(guiAtlas, type, true);
            data.put(type, new HeartColor(normal, blink));
        }
        return data;
    }

    private static int extractColor(SpriteLoader.Preparations guiAtlas, Hud.HeartType type, boolean blink) {
        TextureAtlasSprite sprite = guiAtlas.getSprite(type.getSprite(false, false, blink));
        if (sprite == null) {
            return WHITE.normal;
        }

        int avgColor = 0;
        NativeImage image = sprite.contents().getOriginalImage();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixel(x, y);
                if (ARGB.alpha(color) == 0) {
                    continue;
                }
                if (avgColor == 0) {
                    avgColor = color;
                } else {
                    avgColor = ARGB.average(avgColor, color);
                }
            }
        }
        return avgColor;
    }

    private void apply(Map<Hud.HeartType, HeartColor> data) {
        this.colors = data;
    }

    public HeartColor getHeartColor(Hud.HeartType type) {
        return colors.getOrDefault(type, WHITE);
    }

    public record HeartColor(int normal, int blink) { }
}
