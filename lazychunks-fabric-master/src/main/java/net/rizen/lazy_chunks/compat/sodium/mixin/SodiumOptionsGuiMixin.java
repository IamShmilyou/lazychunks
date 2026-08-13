package net.rizen.lazy_chunks.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.screens.Screen;
import net.rizen.lazy_chunks.compat.sodium.LazyChunksLegacySodiumPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", remap = false)
public abstract class SodiumOptionsGuiMixin {
    @Shadow
    @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lazychunks$addLazyChunksPage(Screen previousScreen, CallbackInfo ci) {
        this.pages.add(LazyChunksLegacySodiumPage.create());
    }
}
