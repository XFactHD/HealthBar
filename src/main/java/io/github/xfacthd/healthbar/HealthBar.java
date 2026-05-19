package io.github.xfacthd.healthbar;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod(value = HealthBar.MOD_ID, dist = Dist.CLIENT)
@SuppressWarnings("UtilityClassWithPublicConstructor")
public final class HealthBar {
    public static final String MOD_ID = "healthbar";

    public HealthBar(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(HealthBar::onRegisterGuiLayers);
        modBus.addListener(HealthBar::onAddReloadListeners);

        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, HealthBarLayer::new);
    }

    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(HeartColorCache.ID, HeartColorCache.INSTANCE);
    }

    public static final class Config {
        private static final ModConfigSpec SPEC;
        public static final ModConfigSpec.EnumValue<HealthBarLayer.DisplayMode> DISPLAY_MODE;
        public static final ModConfigSpec.IntValue THRESHOLD;
        public static final ModConfigSpec.BooleanValue HIDE_NUMS_IN_HARDCORE;

        static {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

            DISPLAY_MODE = builder
                    .comment(
                            "Controls how the health value is displayed.",
                            " - ABSOLUTE: displays the current and max health as absolute values",
                            " - PERCENT: displays the current health as a percentage of max health"
                    )
                    .defineEnum("display", HealthBarLayer.DisplayMode.ABSOLUTE);
            THRESHOLD = builder
                    .comment("Controls the threshold above which the vanilla health display is replaced by a health bar")
                    .defineInRange("threshold", 0, 0, 1024);
            HIDE_NUMS_IN_HARDCORE = builder
                    .comment("Whether health numbers are hidden in hardcore mode")
                    .define("hideNumbersInHardcore", false);

            SPEC = builder.build();
        }

        private Config() { }
    }
}
