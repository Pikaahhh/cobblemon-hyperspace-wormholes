package dev.hyperspacewormholes.hyperspace;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls the third-person camera in while riding in Hyperspace so it doesn't clip into your mount.
 */
public class RideCamera {
    public static float inView = 0.8f;
    private static final double WALL_GAP = 0.2;

    public static Vec3 fit(Camera camera, Entity vehicle, Vec3 pos) {
        Minecraft client = Minecraft.getInstance();
        if (pos == null || client.options.getCameraType().isFirstPerson()) return pos;

        double height = vehicle.getBbHeight();
        double width = vehicle.getBbWidth();
        Vec3 centre = vehicle.getPosition(camera.getPartialTickTime()).add(0, height / 2, 0);

        double tanV = Math.tan(Math.toRadians(client.options.fov().get()) / 2);
        double aspect = (double) client.getWindow().getWidth() / Math.max(1, client.getWindow().getHeight());
        double tanH = tanV * aspect;
        double needed = Math.max(inView * height / 2 / tanV, inView * width / 2 / tanH) + width / 2;

        Vec3 away = pos.subtract(centre);
        double distance = away.length();
        if (distance >= needed) return pos;
        Vec3 dir = distance > 0.01 ? away.scale(1 / distance) : new Vec3(camera.getLookVector()).scale(-1);

        Vec3 target = centre.add(dir.scale(needed));
        BlockHitResult hit = vehicle.level().clip(new ClipContext(pos, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, vehicle));
        if (hit.getType() == HitResult.Type.MISS) return target;
        Vec3 stop = hit.getLocation().subtract(dir.scale(WALL_GAP));
        return stop.subtract(centre).length() > distance ? stop : pos;
    }
}
