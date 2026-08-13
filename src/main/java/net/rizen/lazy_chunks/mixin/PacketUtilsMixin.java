package net.rizen.lazy_chunks.mixin;

import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.thread.ThreadExecutor;
import net.rizen.lazy_chunks.util.PacketRunnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetworkThreadUtils.class)
public abstract class PacketUtilsMixin {

    @Redirect(
            method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/thread/ThreadExecutor;executeSync(Ljava/lang/Runnable;)V"
            ),
            require = 0
    )
    private static <T extends PacketListener> void wrapWithPacketRunnable(
            ThreadExecutor<?> eventLoop,
            Runnable runnable,
            Packet<T> packet,
            T listener,
            ThreadExecutor<?> eventLoopArg
    ) {
        eventLoop.executeSync(new PacketRunnable(packet, runnable));
    }
}
