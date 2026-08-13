package net.rizen.lazy_chunks.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.gui.LazyChunksConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    private VideoSettingsScreenMixin() {
        super(null, null, Component.empty());
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void lazychunks$addConfigButton(CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            return;
        }

        this.list.addHeader(Component.translatable("lazy_chunks.options.video_settings.header"));
        this.list.addSmall(
                Button.builder(Component.translatable("lazy_chunks.options.open"), button ->
                        Minecraft.getInstance().setScreen(new LazyChunksConfigScreen((Screen) (Object) this)))
                        .width(310)
                        .build(),
                null
        );
    }
}
