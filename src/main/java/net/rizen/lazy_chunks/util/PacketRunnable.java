package net.rizen.lazy_chunks.util;

import net.minecraft.network.protocol.Packet;

public class PacketRunnable implements Runnable {
    private final Packet<?> packet;
    private final Runnable runnable;

    public PacketRunnable(Packet<?> packet, Runnable runnable) {
        this.packet = packet;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        this.runnable.run();
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    @Override
    public String toString() {
        return "PacketRunnable: " + this.packet;
    }
}
