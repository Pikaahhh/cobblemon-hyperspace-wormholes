package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.client.cinematic.UltraBurstCinematic;
import dev.hyperspacewormholes.wormholes.network.UltraBurstPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the Ultra Burst when UltraBurstPayload arrives: the game's own cutscene (UltraBurstCinematic) when its camera
 * file loads, and otherwise this simpler scene for the payload's ticks: light gathers into Necrozma (end rods drawn in
 * from all round, sparks circling faster and faster, rising sounds) and the screen whites out over the last FLASH_IN
 * ticks, holding until the server has put Ultra Necrozma in its place and fading over FLASH_OUT. The camera isn't
 * moved in the simpler scene.
 */
public final class UltraBurstScene {
    private static final int FLASH_IN = 20, FLASH_OUT = 24, HOLD = 4;
    private static int entityId = -1, total, age = -1;
    private static Vec3 centre = Vec3.ZERO;

    private UltraBurstScene() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register(UltraBurstScene::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> age = -1);
    }

    public static void start(UltraBurstPayload payload) {
        if (UltraBurstCinematic.play(payload.entityId(), -1)) return;
        entityId = payload.entityId();
        total = payload.ticks();
        age = 0;
        follow();
    }

    private static void follow() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity != null) centre = entity.position().add(0, entity.getBbHeight() * 0.55, 0);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (age < 0 || mc.level == null || mc.isPaused()) return;
        age++;
        if (age > total + HOLD + FLASH_OUT) {
            age = -1;
            return;
        }
        follow();
        if (age > total) return;
        float t = age / (float) total;
        int gather = (int) (2 + 10 * t);
        RandomSource random = mc.level.getRandom();
        for (int i = 0; i < gather; i++) {
            double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI;
            double r = 5 + random.nextDouble() * 4;
            Vec3 from = centre.add(Math.cos(a) * Math.cos(b) * r, Math.sin(b) * r, Math.sin(a) * Math.cos(b) * r);
            Vec3 v = centre.subtract(from).scale(0.09);
            mc.level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
        }
        double spin = age * (0.15 + 0.5 * t);
        for (int k = 0; k < 3; k++) {
            double a = spin + k * Math.PI * 2 / 3;
            double r = 3.2 * (1 - 0.6 * t);
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, centre.x + Math.cos(a) * r, centre.y + Math.sin(age * 0.2 + k) * 1.2,
                    centre.z + Math.sin(a) * r, 0, 0.02, 0);
        }
        if (age == 1) sound(SoundEvents.BEACON_POWER_SELECT, 2f, 0.5f);
        if (age == total * 2 / 5) sound(SoundEvents.BEACON_AMBIENT, 3f, 0.7f);
        if (age == total - FLASH_IN) sound(SoundEvents.BEACON_ACTIVATE, 3f, 1.4f);
        if (age == total) {
            sound(SoundEvents.TOTEM_USE, 2f, 1.2f);
            for (int i = 0; i < 80; i++) {
                double a = i * Math.PI * 2 / 80;
                mc.level.addParticle(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, Math.cos(a) * 0.6, (i % 5 - 2) * 0.12, Math.sin(a) * 0.6);
            }
        }
    }

    private static void sound(SoundEvent event, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(centre.x, centre.y, centre.z, event, SoundSource.NEUTRAL, volume, pitch, false);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (age < 0) return;
        float now = age + delta.getGameTimeDeltaPartialTick(false);
        float white;
        if (now < total - FLASH_IN) white = 0;
        else if (now < total) white = (now - (total - FLASH_IN)) / FLASH_IN;
        else if (now < total + HOLD) white = 1;
        else white = 1 - (now - total - HOLD) / FLASH_OUT;
        white = Mth.clamp(white, 0, 1);
        if (white <= 0) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (white * 255) << 24) | 0xFFFFFF);
    }
}
