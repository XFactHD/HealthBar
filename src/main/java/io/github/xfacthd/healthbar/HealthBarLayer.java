package io.github.xfacthd.healthbar;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.Objects;

// TODO: find a way to indicate hardcore mode
public final class HealthBarLayer implements GuiLayer {
    private static final Identifier BAR_TEXTURE = HealthBar.id("health_bar");
    private static final Identifier REGEN_TEXTURE = HealthBar.id("regen_cursor");
    private static final int BAR_WIDTH = 81;
    private static final int BAR_INNER_WIDTH = BAR_WIDTH - 2;
    private static final int BAR_HEIGHT = 11;
    private static final int BAR_INNER_HEIGHT = BAR_HEIGHT - 2;
    private static final int BAR_VANILLA_HEIGHT = 9;
    private static final int BAR_HEIGHT_DIFF = BAR_HEIGHT - BAR_VANILLA_HEIGHT;
    private static final int BAR_OFF_X = 91;
    private static final int REGEN_H_BLANK = 5;

    private final GuiLayer vanillaLayer;
    private int lastHealth;
    private int displayHealth;
    private long lastHealthTime;
    private long healthBlinkTime;

    public HealthBarLayer(GuiLayer vanillaLayer) {
        this.vanillaLayer = vanillaLayer;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Hud hud = minecraft.gui.hud;
        Player player = hud.getCameraPlayer();
        if (player == null || hud.isHidden() || !Objects.requireNonNull(minecraft.gameMode).canHurtPlayer()) {
            return;
        }

        double maxPlayerHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
        if ((int) maxPlayerHealth < HealthBar.Config.THRESHOLD.getAsInt()) {
            vanillaLayer.render(graphics, deltaTracker);
            return;
        }

        Hud.HeartType type = Hud.HeartType.forPlayer(player);
        boolean hardcore = player.level().getLevelData().isHardcore();
        int currHealth = Mth.ceil(player.getHealth());
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        boolean critical = (currHealth + absorption) <= 4F;
        int tickCount = hud.getGuiTicks();
        boolean blink = healthBlinkTime > tickCount && (healthBlinkTime - tickCount) / 3L % 2L == 1L;
        long timeMillis = Util.getMillis();
        if (currHealth < lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = timeMillis;
            healthBlinkTime = tickCount + 20;
        } else if (currHealth > lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = timeMillis;
            healthBlinkTime = tickCount + 10;
        }

        if (timeMillis - lastHealthTime > 1000L) {
            displayHealth = currHealth;
            lastHealthTime = timeMillis;
        }

        lastHealth = currHealth;
        int oldHealth = displayHealth;
        float maxHealth = Math.max((float) maxPlayerHealth, (float) Math.max(oldHealth, currHealth));
        float healthPercent = (currHealth / maxHealth);

        hud.random.setSeed(tickCount * 312871L);
        int barX = graphics.guiWidth() / 2 - BAR_OFF_X;
        int barInnerX = barX + 1;
        int barY = graphics.guiHeight() - hud.leftHeight - BAR_HEIGHT_DIFF;
        int barInnerY = barY + 1;
        hud.leftHeight += BAR_HEIGHT + 1;
        hud.rightHeight += BAR_HEIGHT_DIFF / 2;
        int barWidth = (int) (BAR_INNER_WIDTH * healthPercent);
        HeartColorCache.HeartColor heartColor = HeartColorCache.INSTANCE.getHeartColor(type);

        Profiler.get().push("health");

        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, CommonColors.BLACK);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_TEXTURE, BAR_INNER_WIDTH, BAR_INNER_HEIGHT, 0, 0, barInnerX, barInnerY, barWidth, BAR_INNER_HEIGHT, heartColor.normal());
        if (blink) {
            graphics.outline(barX, barY, BAR_WIDTH, BAR_HEIGHT, CommonColors.WHITE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_TEXTURE, BAR_INNER_WIDTH, BAR_INNER_HEIGHT, 0, 0, barInnerX + barWidth, barInnerY, BAR_INNER_WIDTH - barWidth, BAR_INNER_HEIGHT, heartColor.blink());
        } else if (critical && (tickCount / 3) % 2 == 0) {
            graphics.outline(barX, barY, BAR_WIDTH, BAR_HEIGHT, CommonColors.RED);
        }
        if (absorption > 0 && type != Hud.HeartType.WITHERED) {
            // TODO: this isn't a perfect solution, particularly because the absorption attribute has a 2048 limit while health has 1024
            int absorptionWidth = (int) (BAR_INNER_WIDTH * Math.min(absorption / maxHealth, 1F));
            int color = HeartColorCache.INSTANCE.getHeartColor(Hud.HeartType.ABSORBING).normal();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_TEXTURE, BAR_INNER_WIDTH, BAR_INNER_HEIGHT, 0, 0, barInnerX, barInnerY, absorptionWidth, BAR_INNER_HEIGHT, color);
        }
        if (player.hasEffect(MobEffects.REGENERATION)) {
            int ticks = (int)(tickCount + deltaTracker.getGameTimeDeltaPartialTick(true));
            int offset = (ticks % (BAR_WIDTH + REGEN_H_BLANK)) - 1;
            if (offset < barWidth) {
                int minX = barInnerX + offset;
                graphics.enableScissor(barInnerX, barInnerY, barInnerX + barWidth, barInnerY + BAR_INNER_HEIGHT);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REGEN_TEXTURE, 3, BAR_INNER_HEIGHT, 0, 0, minX, barInnerY, 3, BAR_INNER_HEIGHT);
                graphics.disableScissor();
            }
        }

        if (!hardcore || !HealthBar.Config.HIDE_NUMS_IN_HARDCORE.getAsBoolean()) {
            Font font = minecraft.font;
            switch (HealthBar.Config.DISPLAY_MODE.get()) {
                case ABSOLUTE -> {
                    String currText = Integer.toString(currHealth + absorption);
                    String maxText = Integer.toString((int) maxHealth);
                    String text = currText + "/" + maxText;
                    int textX = barX + (BAR_WIDTH / 2 + 1) - (font.width("/") / 2) - font.width(currText);
                    graphics.text(font, text, textX, barY + 2, 0xFFFFFFFF, false);
                }
                case PERCENT -> {
                    float totalPercent = healthPercent + (absorption / maxHealth);
                    int percent = (int) (totalPercent * 100);
                    String text = percent + "%";
                    int width = font.width(text);
                    int textX = barX + (BAR_WIDTH / 2 + 1) - (width / 2);
                    graphics.text(font, text, textX, barY + 2, 0xFFFFFFFF, false);
                }
            }
        }

        Profiler.get().pop();
    }

    public enum DisplayMode {
        ABSOLUTE,
        PERCENT,
    }
}
