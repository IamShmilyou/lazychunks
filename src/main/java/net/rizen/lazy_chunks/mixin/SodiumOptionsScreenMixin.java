package net.rizen.lazy_chunks.mixin;

import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.rizen.lazy_chunks.gui.SodiumCompat;
import net.rizen.lazy_chunks.gui.SodiumOptionsPageFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI", remap = false)
public abstract class SodiumOptionsScreenMixin extends Screen {
    @Shadow
    @Final
    private List<OptionPage> pages;

    private SodiumOptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void lazychunks$addConfigPage(@Coerce Object parent, CallbackInfo ci) {
        if (SodiumCompat.isSodiumOptionsApiLoaded()) {
            return;
        }

        for (OptionPage page : this.pages) {
            if ("LazyChunks".equals(page.getName().getString())) {
                return;
            }
        }

        this.pages.add(SodiumOptionsPageFactory.createPage());
    }
}
