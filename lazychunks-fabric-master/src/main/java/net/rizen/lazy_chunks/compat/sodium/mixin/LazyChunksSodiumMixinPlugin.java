package net.rizen.lazy_chunks.compat.sodium.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class LazyChunksSodiumMixinPlugin implements IMixinConfigPlugin {
    private static final String LEGACY_SODIUM_GUI = "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isModLoaded("sodium")
                && !FabricLoader.getInstance().isModLoaded("sodiumoptionsapi")
                && isClassPresent(LEGACY_SODIUM_GUI)
                && !isClassPresent("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, LazyChunksSodiumMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
