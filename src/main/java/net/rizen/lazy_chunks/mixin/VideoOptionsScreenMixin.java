package net.rizen.lazy_chunks.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import net.rizen.lazy_chunks.gui.LazyChunksConfigScreen;
import net.rizen.lazy_chunks.gui.SodiumCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin extends GameOptionsScreen {
    private VideoOptionsScreenMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void lazychunks$addConfigButton(CallbackInfo ci) {
        if (this.client == null || SodiumCompat.isSodiumLoaded()) {
            return;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("lazy_chunks.video_button"), button ->
                this.client.setScreen(new LazyChunksConfigScreen(this))
        ).dimensions(this.width / 2 - 75, this.height - 52, 150, 20).build());
    }
}
