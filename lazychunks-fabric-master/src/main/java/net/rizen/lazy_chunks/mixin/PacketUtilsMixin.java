package net.rizen.lazy_chunks.mixin;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.util.thread.BlockableEventLoop;
import net.rizen.lazy_chunks.util.PacketRunnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @Redirect(
            method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/thread/BlockableEventLoop;executeIfPossible(Ljava/lang/Runnable;)V"
            ),
            require = 0
    )
    private static <T extends PacketListener> void wrapWithPacketRunnable(
            BlockableEventLoop<?> eventLoop,
            Runnable runnable,
            Packet<T> packet,
            T listener,
            BlockableEventLoop<?> eventLoopArg
    ) {
        eventLoop.executeIfPossible(new PacketRunnable(packet, runnable));
    }
}
