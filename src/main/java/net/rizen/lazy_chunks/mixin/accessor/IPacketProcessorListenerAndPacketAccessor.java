package net.rizen.lazy_chunks.mixin.accessor;

import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public interface IPacketProcessorListenerAndPacketAccessor {

    @Accessor("packet")
    Packet<?> lazychunks$getPacket();

    @Invoker("handle")
    void lazychunks$handle();
}
