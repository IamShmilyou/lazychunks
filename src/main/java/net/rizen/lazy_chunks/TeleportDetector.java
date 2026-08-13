package net.rizen.lazy_chunks;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class TeleportDetector {

    private static Vec3 lastPosition = null;
    private static ResourceKey<Level> lastDimension = null;
    private static int teleportCooldown = 0;
    private static final double TELEPORT_DISTANCE_THRESHOLD = 64.0;
    private static final int TELEPORT_COOLDOWN_FRAMES = 120;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;

        if (player == null || level == null) {
            reset();
            return;
        }

        Vec3 currentPos = player.position();
        ResourceKey<Level> currentDim = level.dimension();

        if (lastPosition != null && lastDimension != null) {
            if (!sameDimension(currentDim, lastDimension) || lastPosition.distanceTo(currentPos) > TELEPORT_DISTANCE_THRESHOLD) {
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

    private static boolean sameDimension(ResourceKey<Level> left, ResourceKey<Level> right) {
        if (left == right) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        return Objects.equals(left.registry(), right.registry()) && Objects.equals(left.identifier(), right.identifier());
    }
}
