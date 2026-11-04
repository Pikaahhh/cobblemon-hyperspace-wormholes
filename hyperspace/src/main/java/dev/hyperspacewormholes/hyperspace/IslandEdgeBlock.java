package dev.hyperspacewormholes.hyperspace;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stops players (and whatever they're riding) walking off the edge of an island into the void.
 */
public class IslandEdgeBlock {
    private static final double DEPTH = 64;
    private static final double MARGIN = 0.5;
    private static final double AWAY = 1.0;

    private static Vec3 bumped;

    public static Vec3 clamp(Player player, Vec3 move) {
        if (player.getAbilities().flying || player.isFallFlying()) return move;
        return clamp(player, player, move);
    }

    public static Vec3 clamp(Entity mover, Player player, Vec3 move) {
        if (!mover.level().dimension().equals(Hyperspace.HYPERSPACE) || player.isSpectator()) return move;
        AABB box = mover.getBoundingBox();
        int now = support(mover, box);
        double x = move.x, z = move.z;
        if (x != 0 && worse(mover, box.move(x, 0, 0), now)) x = 0;
        if (z != 0 && worse(mover, box.move(x, 0, z), now)) z = 0;
        if (x == move.x && z == move.z) {
            if (player.level().isClientSide() && bumped != null && bumped.distanceToSqr(player.getX(), bumped.y, player.getZ()) > AWAY * AWAY) bumped = null;
            return move;
        }
        bump(player);
        return new Vec3(x, move.y, z);
    }

    private static void bump(Player player) {
        if (!player.level().isClientSide()) return;
        if (bumped == null) player.playSound(ModSounds.WALL_BUMP, 0.8f, 1f);
        bumped = player.position();
    }

    private static boolean worse(Entity mover, AABB box, int now) {
        int next = support(mover, box);
        return next < 4 && next < now;
    }

    /** How many of the box's corners, pushed out by MARGIN, have ground somewhere below them. */
    private static int support(Entity mover, AABB box) {
        double x0 = box.minX - MARGIN, x1 = box.maxX + MARGIN, z0 = box.minZ - MARGIN, z1 = box.maxZ + MARGIN;
        return below(mover, box, x0, z0) + below(mover, box, x0, z1) + below(mover, box, x1, z0) + below(mover, box, x1, z1);
    }

    private static int below(Entity mover, AABB box, double x, double z) {
        AABB probe = new AABB(x - 1.0E-3, box.minY - DEPTH, z - 1.0E-3, x + 1.0E-3, box.minY - 1.0E-3, z + 1.0E-3);
        return mover.level().noCollision(mover, probe) ? 0 : 1;
    }
}
