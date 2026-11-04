package dev.hyperspacewormholes.wormholes.client.cinematic;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.ramp;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.smooth;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.tint;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.window;

/**
 * The light of the Ultra Burst cutscene (UltraBurstCinematic), timed to the game's frames (30 a second): a glowing ball
 * round Necrozma, light streaking into it, the forehead flash, the core coming out, then a burst of prism shards and
 * mist as Ultra Necrozma comes down.
 * The core is a part of the model (CORE_ASPECT) glowing through its glow layer, which is lit like any face, so a glow
 * stretched along it blows it out to white with a bloom. Screen tints follow the brightness of the game's frames.
 */
public final class UltraBurstEffects {
    private static final int STREAKS = 18, TRAIL = 12, SHARDS = 70;
    private static final int[] SHARD_COLOURS = {0xFF6FD8, 0x6FF2FF, 0x9CFF7A, 0xFFF06A, 0xB58CFF, 0xFFFFFF};
    private static float[][] streaks = new float[0][];
    private static float[][] shards = new float[0][];

    private UltraBurstEffects() {}

    static void start(boolean dawn) {
        RandomSource random = RandomSource.create(dawn ? 791 : 792);
        streaks = new float[STREAKS][];
        for (int i = 0; i < STREAKS; i++) {
            Vector3f axis = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() * 0.6f + 0.2f, random.nextFloat() - 0.5f).normalize();
            streaks[i] = new float[]{axis.x, axis.y, axis.z, random.nextFloat() * Mth.TWO_PI,
                    (0.07f + random.nextFloat() * 0.08f) * (random.nextBoolean() ? 1 : -1), 0.55f + random.nextFloat() * 0.45f,
                    random.nextFloat() * 170, 35 + random.nextFloat() * 30};
        }
        shards = new float[SHARDS][];
        for (int i = 0; i < SHARDS; i++) {
            Vector3f dir = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian() * 0.6f + 0.2f, (float) random.nextGaussian()).normalize();
            int colour = SHARD_COLOURS[random.nextInt(SHARD_COLOURS.length)];
            shards[i] = new float[]{dir.x, dir.y, dir.z, 0.22f + random.nextFloat() * 0.3f, 300 + random.nextFloat() * 40,
                    22 + random.nextFloat() * 16, 0.12f + random.nextFloat() * 0.2f,
                    (colour >> 16 & 255) / 255f, (colour >> 8 & 255) / 255f, (colour & 255) / 255f};
        }
    }

    static void tick(double f, Vec3 boss, float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RandomSource random = mc.level.getRandom();
        Vec3 centre = UltraBurstCinematic.domeCentre();
        if (f < 200) {
            for (int i = 0; i < 3; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8, random.nextGaussian()).normalize();
                Vec3 from = centre.add(dir.scale(11 + random.nextDouble() * 6));
                Vec3 v = centre.subtract(from).scale(0.07);
                mc.level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
            }
        }
        if (f >= 150 && f < 228) {
            for (int i = 0; i < 4; i++) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, centre.x + random.nextGaussian() * 1.5,
                        centre.y + random.nextGaussian() * 1.2, centre.z + random.nextGaussian() * 1.5, 0, 0.05, 0);
            }
        }
        if (f >= 440 && f < 570) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Mth.TWO_PI, r = 2 + random.nextDouble() * 9;
                mc.level.addParticle(ParticleTypes.CLOUD, boss.x + Math.cos(a) * r, boss.y + 0.15, boss.z + Math.sin(a) * r,
                        Math.cos(a) * 0.02, 0.004, Math.sin(a) * 0.02);
            }
        }
        if (f >= 370) {
            for (int i = 0; i < 2; i++) {
                mc.level.addParticle(ParticleTypes.END_ROD, boss.x + random.nextGaussian() * 2.5, boss.y + random.nextDouble() * height,
                        boss.z + random.nextGaussian() * 2.5, 0, 0.03, 0);
            }
        }
    }

    public static void collect(WorldRenderContext context) {
        if (!UltraBurstCinematic.isPlaying()) return;
        double f = UltraBurstCinematic.frame(context.tickCounter().getGameTimeDeltaPartialTick(false));
        GlowQuads q = new GlowQuads(context);
        Vec3 boss = UltraBurstCinematic.boss();
        float height = UltraBurstCinematic.bossHeight();
        Vec3 centre = boss.add(0, height * 0.45, 0);
        Vec3 domeAt = UltraBurstCinematic.domeCentre();
        double domeRadius = UltraBurstCinematic.domeRadius();
        float dome = window(f, 0, 25, 236, 262);
        if (dome > 0) dome(q, domeAt, 1.5 + (domeRadius - 1.5) * smooth(f, 0, 45), boss.y, dome);
        if (f < 200) streaks(q, f, domeAt, window(f, 0, 10, 180, 200), domeRadius);
        float core = window(f, 120, 280, 300, 314);
        if (core > 0) {
            q.sprite(centre, (float) (1.5 + 3.5 * smooth(f, 150, 290)), 1f, 1f, 0.75f, core);
            q.sprite(centre, (float) (4 + 6 * smooth(f, 150, 290)), 0.9f, 1f, 0.5f, core * 0.35f);
        }
        Vec3 coreAt = UltraBurstCinematic.coreCentre();
        float crystal = window(f, 176, 182, 290, 296);
        if (coreAt != null && crystal > 0 && UltraBurstCinematic.coreAxis().lengthSqr() > 1e-4) {
            Vec3 axis = UltraBurstCinematic.coreAxis();
            stretched(q, coreAt, axis, 1.1, 0.5, 1f, 1f, 1f, 0.7f * crystal);
            stretched(q, coreAt, axis, 1.5, 0.9, 0.85f, 0.95f, 1f, 0.15f * crystal);
        }
        float flash = window(f, 172, 182, 192, 210);
        if (flash > 0) {
            float s = UltraBurstCinematic.sizeFactor();
            q.sprite(UltraBurstCinematic.headTop(), (0.5f + 1.3f * flash) * s, 1f, 1f, 0.95f, flash);
            q.sprite(UltraBurstCinematic.headTop(), (1.2f + 2.4f * flash) * s, 0.75f, 1f, 1f, flash * 0.35f);
        }
        float burst = window(f, 294, 304, 355, 372);
        if (burst > 0) burst(q, f, centre, burst);
        if (f >= 300 && f < 380) shards(q, f, centre);
        float aura = window(f, 370, 400, 680, 698);
        if (aura > 0) {
            Vec3 middle = boss.add(0, height * 0.55, 0);
            float pulse = 0.8f + 0.2f * Mth.sin((float) f * 0.15f);
            q.sprite(middle, Math.max(4, height * 0.7f), 1f, 0.95f, 0.75f, aura * 0.18f * pulse);
        }
        q.draw();
    }

    private static void dome(GlowQuads q, Vec3 centre, double radius, double floor, float alpha) {
        int around = 40, rings = 14;
        Vec3 cam = q.cam();
        double low = Math.asin(Mth.clamp((floor - centre.y) / radius, -1, 1)), span = Math.PI / 2 - low;
        float inside = (float) Mth.clamp((radius - cam.distanceTo(centre)) / 1.5, 0, 1);
        Vec3[][] points = new Vec3[rings + 1][around + 1];
        float[][] glow = new float[rings + 1][around + 1];
        for (int i = 0; i <= rings; i++) {
            double lat = low + span * i / rings;
            for (int k = 0; k <= around; k++) {
                double lon = Mth.TWO_PI * k / around;
                Vec3 n = new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
                Vec3 p = centre.add(n.scale(radius));
                Vec3 view = p.subtract(cam).normalize();
                double rim = 1 - Math.abs(n.dot(view));
                double outside = 0.05 + 0.75 * Math.pow(rim, 3) + 0.25 * Math.pow(1 - (lat - low) / span, 6);
                glow[i][k] = (float) Mth.lerp(inside, outside, 0.7 + 0.3 * rim) * alpha;
                points[i][k] = p;
            }
        }
        float r = Mth.lerp(inside, 0.85f, 1f), g = Mth.lerp(inside, 1f, 0.96f), b = Mth.lerp(inside, 0.55f, 0.78f);
        for (int i = 0; i < rings; i++) {
            for (int k = 0; k < around; k++) {
                q.quad(points[i][k], points[i][k + 1], points[i + 1][k + 1], points[i + 1][k], r, g, b,
                        glow[i][k], glow[i][k + 1], glow[i + 1][k + 1], glow[i + 1][k]);
            }
        }
    }

    private static void streaks(GlowQuads q, double f, Vec3 centre, float alpha, double domeRadius) {
        Vec3[] trail = new Vec3[TRAIL];
        for (float[] s : streaks) {
            double life = (f - s[6]) / s[7];
            if (life < 0 || life > 1) continue;
            Vec3 axis = new Vec3(s[0], s[1], s[2]);
            Vec3 u = axis.cross(new Vec3(0, 1, 0));
            if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
            u = u.normalize();
            Vec3 w = axis.cross(u).normalize();
            float fade = alpha * Mth.sin((float) (life * Math.PI));
            for (int t = 0; t < TRAIL; t++) {
                double age = f - s[6] - t * 1.2;
                double angle = s[3] + s[4] * age;
                double radius = s[5] * domeRadius * (1 - 0.55 * Mth.clamp(age / s[7], 0, 1));
                trail[t] = centre.add(u.scale(Math.cos(angle) * radius)).add(w.scale(Math.sin(angle) * radius));
            }
            taper(q, trail, 0.12f, 1f, 1f, 0.85f, fade);
        }
    }

    /** A ribbon narrowing from full width at its head to nothing at its tail. */
    private static void taper(GlowQuads q, Vec3[] trail, float width, float r, float g, float b, float alpha) {
        Vec3 cam = q.cam();
        for (int t = 0; t < trail.length - 1; t++) {
            Vec3 a = trail[t], c = trail[t + 1];
            Vec3 side = c.subtract(a).cross(a.subtract(cam));
            if (side.lengthSqr() < 1e-8) continue;
            side = side.normalize();
            float k0 = 1 - t / (float) (trail.length - 1), k1 = 1 - (t + 1) / (float) (trail.length - 1);
            Vec3 s0 = side.scale(width * k0), s1 = side.scale(width * k1);
            q.quad(a.add(s0), a.subtract(s0), c.subtract(s1), c.add(s1), r, g, b, alpha * k0, alpha * k0, alpha * k1, alpha * k1);
        }
    }

    private static void stretched(GlowQuads q, Vec3 at, Vec3 axis, double along, double across, float r, float g, float b, float alpha) {
        double length = axis.length();
        Vec3 side = axis.cross(at.subtract(q.cam()));
        side = side.lengthSqr() < 1e-8 ? q.right() : side.normalize();
        Vec3 ax = axis.scale(along), sx = side.scale(across * length);
        q.textured(at.subtract(ax).subtract(sx), at.subtract(ax).add(sx), at.add(ax).add(sx), at.add(ax).subtract(sx), r, g, b, alpha);
    }

    private static void shards(GlowQuads q, double f, Vec3 centre) {
        for (float[] s : shards) {
            double age = f - s[4];
            if (age < 0 || age > s[5]) continue;
            double life = age / s[5];
            Vec3 at = centre.add(new Vec3(s[0], s[1], s[2]).scale(s[3] * age * (1 - 0.35 * life)));
            float alpha = (float) (Math.min(1, age / 3) * (1 - life));
            q.sprite(at, s[6], s[7], s[8], s[9], alpha);
            q.sprite(at, s[6] * 0.45f, 1f, 1f, 1f, alpha * 0.8f);
        }
    }

    private static void burst(GlowQuads q, double f, Vec3 centre, float alpha) {
        float grow = smooth(f, 294, 330);
        q.sprite(centre, 6 + 10 * grow, 1f, 0.85f, 0.55f, alpha);
        q.sprite(centre, 3 + 4 * grow, 1f, 1f, 0.95f, alpha);
        Vec3 rx = q.right(), uy = q.up();
        RandomSource random = RandomSource.create(800);
        int rays = 30;
        for (int i = 0; i < rays; i++) {
            double angle = Mth.TWO_PI * i / rays + random.nextDouble() * 0.15 + f * 0.004;
            double length = (10 + random.nextDouble() * 22) * (0.4 + 0.6 * grow);
            double width = 0.25 + random.nextDouble() * 0.45;
            Vec3 dir = rx.scale(Math.cos(angle)).add(uy.scale(Math.sin(angle)));
            Vec3 side = rx.scale(-Math.sin(angle)).add(uy.scale(Math.cos(angle))).scale(width);
            Vec3 tip = centre.add(dir.scale(length));
            float warm = random.nextFloat() * 0.25f;
            q.quad(centre.add(side), centre.subtract(side), tip, tip, 1f, 1f - warm, 0.8f - warm, alpha * 0.8f, alpha * 0.8f, 0, 0);
        }
    }

    static void screen(GuiGraphics g, double f, int w, int h) {
        tint(g, w, h, 1 - ramp(f, 0, 6), 0x000000);
        tint(g, w, h, 0.2f * window(f, 172, 178, 226, 240), 0xFFF2C8);
        tint(g, w, h, 0.85f * window(f, 284, 290, 310, 315), 0xFFFFFF);
        if (f >= 315 && f < 370) tint(g, w, h, Mth.lerp(smooth(f, 315, 362), 0.35f, 0.92f), 0xFFC27A);
        if (f >= 358 && f < 370) tint(g, w, h, smooth(f, 358, 366), 0xFFFFFF);
        if (f >= 370 && f < 400) {
            float t = ramp(f, 370, 394);
            tint(g, w, h, Mth.lerp(t, 0.95f, 0.55f) * (1 - smooth(f, 393, 400)), mix(0xFFF4C0, 0xF2D266, t));
        }
        tint(g, w, h, 0.2f * window(f, 396, 400, 404, 408), 0x50306E);
        tint(g, w, h, 0.85f * window(f, 408, 410, 412, 436), 0xE6FF8C);
        tint(g, w, h, 0.15f * window(f, 454, 470, 540, 562), 0xD8E0F0);
        tint(g, w, h, ramp(f, 690, 698), 0x000000);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
        int gr = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return r << 16 | gr << 8 | bl;
    }
}
