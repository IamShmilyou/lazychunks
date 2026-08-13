package net.rizen.lazy_chunks.mixin;

import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

@Mixin(BlockableEventLoop.class)
public interface PendingTaskQueueAccessor {
    @Accessor("pendingRunnables")
    Queue<Runnable> lazychunks$getPendingRunnables();
}
