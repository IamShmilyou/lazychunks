package net.rizen.lazy_chunks.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.rizen.lazy_chunks.LazyChunksMod;

@Mod.EventBusSubscriber(modid = LazyChunksMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LazyChunksVideoSettingsIntegration {
    private LazyChunksVideoSettingsIntegration() {
    }

    @SubscribeEvent
    public static void onVideoSettingsInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof VideoSettingsScreen screen)) {
            return;
        }

        if (LazyChunksClientIntegration.isSodiumOptionsApiLoaded()) {
            return;
        }

        int buttonWidth = 150;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = screen.height - 51;

        event.addListener(Button.builder(Component.translatable("lazy_chunks.screen.title"), button -> {
                    if (screen.getMinecraft() != null) {
                        screen.getMinecraft().setScreen(new LazyChunksConfigScreen(screen));
                    }
                })
                .bounds(x, y, buttonWidth, 20)
                .build());
    }
}
