package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.rizen.lazy_chunks.screen.LazyChunksConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsSubScreen.class)
public abstract class OptionsSubScreenMixin {

    @Shadow
    protected OptionsList list;

    @Inject(method = "addContents", at = @At("TAIL"))
    private void lazychunks$addVideoSettingsConfigButton(CallbackInfo ci) {
        if (!((Object) this instanceof VideoSettingsScreen)) {
            return;
        }

        Button configButton = Button.builder(
                        Component.translatable("lazy_chunks.options.video_button"),
                        button -> Minecraft.getInstance().setScreenAndShow(new LazyChunksConfigScreen((Screen) (Object) this))
                )
                .tooltip(Tooltip.create(Component.translatable("lazy_chunks.options.video_button.tooltip")))
                .width(150)
                .build();

        this.list.addHeader(Component.translatable("lazy_chunks.options.title"));
        this.list.addSmall(configButton, null);
    }
}
