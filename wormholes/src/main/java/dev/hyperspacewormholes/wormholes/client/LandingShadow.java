package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** A shadow on the ground where Cliff World's circling Pokemon will land: vanilla's round entity shadow laid flat on the zone's spot while a flying shadow (WildsShadows.isFlyingShadow) is in the level, sized from the Pokemon's width, fainter and wider the higher it is and darker and tighter as it comes down (over FADE_HEIGHT); once it has landed it stops being a shadow and this stops. */
public final class LandingShadow {
    private static final RenderType SHADOW = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    private static final double FADE_HEIGHT = 48;

    private LandingShadow() {}

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Wormholes.WILDS_CLIFF) return;
        Zone zone = UltraZones.of(mc.level);
        if (zone == null || zone.spot() == null) return;
        Entity flier = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (WildsShadows.isFlyingShadow(entity)) {
                flier = entity;
                break;
            }
        }
        if (flier == null) return;
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 spot = zone.standOn(zone.spot());
        double height = Math.max(0, Mth.lerp(partialTick, flier.yOld, flier.getY()) - spot.y);
        float near = (float) Mth.clamp(1 - height / FADE_HEIGHT, 0, 1);
        float alpha = 0.3f + 0.45f * near;
        float radius = Math.max(0.9f, flier.getBbWidth() * 0.9f) * (1.4f - 0.4f * near);
        Vec3 cam = context.camera().getPosition();
        float x = (float) (spot.x - cam.x), y = (float) (spot.y - cam.y + 0.01), z = (float) (spot.z - cam.z);
        PoseStack.Pose pose = new PoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(SHADOW);
        corner(buffer, pose, x - radius, y, z - radius, 0, 0, alpha);
        corner(buffer, pose, x - radius, y, z + radius, 0, 1, alpha);
        corner(buffer, pose, x + radius, y, z + radius, 1, 1, alpha);
        corner(buffer, pose, x + radius, y, z - radius, 1, 0, alpha);
        buffers.endBatch(SHADOW);

    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float alpha) {
        buffer.addVertex(pose, x, y, z).setColor(1f, 1f, 1f, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
