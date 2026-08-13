package net.rizen.lazy_chunks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class TeleportDetector {

    private static Vec3d lastPosition = null;
    private static String lastDimension = null;
    private static int teleportCooldown = 0;
    private static final double TELEPORT_DISTANCE_THRESHOLD = 64.0;
    private static final int TELEPORT_COOLDOWN_FRAMES = 120;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            lastPosition = null;
            lastDimension = null;
            return;
        }

        Vec3d currentPos = mc.player.getPos();
        String currentDim = mc.world.getRegistryKey().getValue().toString();

        if (lastPosition != null && lastDimension != null) {
            if (!currentDim.equals(lastDimension)) {
                teleportCooldown = TELEPORT_COOLDOWN_FRAMES;
            } else if (lastPosition.distanceTo(currentPos) > TELEPORT_DISTANCE_THRESHOLD) {
                teleportCooldown = TELEPORT_COOLDOWN_FRAMES;
            }
        }

        lastPosition = currentPos;
        lastDimension = currentDim;

        if (teleportCooldown > 0) {
            teleportCooldown--;
        }
    }

    public static boolean isTeleportRecovery() {
        return teleportCooldown > 0;
    }

    public static int getCooldownRemaining() {
        return teleportCooldown;
    }

    public static double getBudgetMultiplier() {
        if (teleportCooldown <= 0) {
            return 1.0;
        }

        double progress = 1.0 - ((double) teleportCooldown / TELEPORT_COOLDOWN_FRAMES);
        return 0.3 + (0.7 * progress);
    }

    public static void reset() {
        lastPosition = null;
        lastDimension = null;
        teleportCooldown = 0;
    }
}
