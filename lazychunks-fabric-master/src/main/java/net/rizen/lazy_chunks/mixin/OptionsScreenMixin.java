package net.rizen.lazy_chunks.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.rizen.lazy_chunks.screen.LazyChunksVideoSettingsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {

    @Shadow
    @Final
    private Options options;

    @Inject(method = "method_19828", at = @At("RETURN"), cancellable = true, require = 0)
    private void lazychunks$openLazyChunksVideoSettings(CallbackInfoReturnable<Screen> cir) {
        Screen vanillaVideoSettings = cir.getReturnValue();
        if (FabricLoader.getInstance().isModLoaded("sodium")
                || vanillaVideoSettings.getClass() != VideoSettingsScreen.class) {
            return;
        }

        cir.setReturnValue(new LazyChunksVideoSettingsScreen((Screen) (Object) this, Minecraft.getInstance(), this.options));
    }
}
