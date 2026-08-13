package net.rizen.lazy_chunks.mixin;

import net.minecraft.network.PacketProcessor;
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

        int taskLimit = LazyChunkLoading.getTaskCount(this.packetsToBeHandled);

        if (!LazyChunkLoading.wasThrottled()) {
            return;
        }

        long maxTimeNanos = LazyChunkLoading.getMaxProcessingTimeNanos();
        long startTime = System.nanoTime();

        while (!this.packetsToBeHandled.isEmpty()) {
            if (this.closed) {
                break;
            }

            Object task = this.packetsToBeHandled.poll();
            if (task == null) {
                break;
            }

            if (task instanceof IPacketProcessorListenerAndPacketAccessor packetTask) {
                packetTask.lazychunks$handle();
            } else {
                // Never drop unknown queue entries. Hand them back to vanilla and try again next tick.
                this.packetsToBeHandled.add(task);
                break;
            }

            if (this.closed) {
                break;
            }

            --taskLimit;
            if (taskLimit <= 0) {
                break;
            }

            if (System.nanoTime() - startTime > maxTimeNanos) {
                break;
            }
        }

        LazyChunkLoading.recordProcessingTime((System.nanoTime() - startTime) / 1_000_000.0);
        ci.cancel();
    }
}
