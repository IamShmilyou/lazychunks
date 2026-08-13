package net.rizen.lazy_chunks.mixin;

import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.rizen.lazy_chunks.LazyChunkLoading;
import net.rizen.lazy_chunks.mixin.accessor.IPacketProcessorListenerAndPacketAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @Shadow
    @Final
    private Queue<Object> packetsToBeHandled;

    @Shadow
    private boolean closed;

    @Inject(method = "processQueuedPackets", at = @At("HEAD"), cancellable = true)
    private void lazychunks$throttlePacketProcessing(CallbackInfo ci) {
        if (this.closed || this.packetsToBeHandled.isEmpty()) {
            return;
        }

        if (!LazyChunkLoading.beginFrame(this.packetsToBeHandled)) {
            return;
        }

        long maxTimeNanos = LazyChunkLoading.getMaxProcessingTimeNanos();
        long startTime = System.nanoTime();
        double processedWeight = 0.0D;
        boolean processedWeightedPacket = false;
        int processedTasks = 0;

        while (!this.packetsToBeHandled.isEmpty()) {
            if (this.closed) {
                break;
            }

            Object task = this.packetsToBeHandled.peek();
            if (!(task instanceof IPacketProcessorListenerAndPacketAccessor packetTask)) {
                if (processedTasks == 0) {
                    return;
                }
                break;
            }

            Packet<?> packet = packetTask.lazychunks$getPacket();
            if (!LazyChunkLoading.canProcessPacket(packet, processedWeight, processedWeightedPacket)) {
                break;
            }

            task = this.packetsToBeHandled.poll();
            if (!(task instanceof IPacketProcessorListenerAndPacketAccessor polledPacketTask)) {
                break;
            }

            polledPacketTask.lazychunks$handle();
            processedTasks++;

            double packetWeight = LazyChunkLoading.getPacketWeight(packet);
            if (packetWeight > 0.0D) {
                processedWeight += packetWeight;
                processedWeightedPacket = true;
            }

            if (this.closed) {
                break;
            }

            if (System.nanoTime() - startTime > maxTimeNanos && processedWeightedPacket) {
                break;
            }
        }

        LazyChunkLoading.finishFrame(processedWeight, (System.nanoTime() - startTime) / 1_000_000.0);
        ci.cancel();
    }
}
