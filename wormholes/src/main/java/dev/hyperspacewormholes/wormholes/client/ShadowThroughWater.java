package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a waiting Wilds shadow (WildsShadows) again over the water between it and the camera, faintly (ALPHA), so it reads as a dark shape behind Water World's waterfall, whose front and back faces leave next to nothing of a black shape behind them. Only when water lies on a sight line to the middle, top or one of four sides of the inner part of its box (INSET of the way out).
 * The solid terrain's depth is saved before entities are drawn and at the end of the frame stands in for the depth buffer (which by then holds the water) while the model goes down twice through its own render types: first into depth only, then in colour only where that depth matches, so each spot is shaded once and rock in front hides that part. The frame's depth is put back after.
 * Under a shaderpack it is drawn after water instead, and only when no block is on any of those sight lines. Shadows are never culled (noCulling, set at the start of each frame) so a mod like Entity Culling doesn't drop one in the grotto.
 */
public final class ShadowThroughWater {
    private static final float ALPHA = 0.3f;
    private static final double INSET = 0.35;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private static final Map<RenderType, RenderType> DEPTH = new HashMap<>();
    private static final Map<RenderType, RenderType> COLOUR = new HashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_OVER = new HashMap<>();
    private static final Map<RenderType, RenderType> COLOUR_OVER = new HashMap<>();
    private static @Nullable TextureTarget terrainDepth;
    private static @Nullable TextureTarget frameDepth;
    private static boolean saved;

    private ShadowThroughWater() {}

    public static void keepDrawn(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && UltraZones.isZone(mc.level)) shadows(mc).forEach(entity -> entity.noCulling = true);
    }

    public static void saveTerrainDepth(WorldRenderContext context) {
        saved = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ShaderPacks.inUse() || !UltraZones.isZone(mc.level) || shadows(mc).isEmpty()) return;
        RenderTarget main = mc.getMainRenderTarget();
        terrainDepth = sized(terrainDepth, main);
        terrainDepth.copyDepthFrom(main);
        main.bindWrite(false);
        saved = true;
    }

    public static void drawAfterWater(WorldRenderContext context) {
        if (ShaderPacks.inUse()) drawOver(context);
    }

    public static void drawLast(WorldRenderContext context) {
        if (!ShaderPacks.inUse() && saved) ShaderPacks.inCameraView(context, () -> drawBehind(context));
        saved = false;
    }

    private static void drawBehind(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = context.camera().getPosition();
        List<Entity> shown = shadows(mc).stream().filter(e -> behindWater(mc, cam, e, false)).toList();
        if (shown.isEmpty() || terrainDepth == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        frameDepth = sized(frameDepth, main);
        frameDepth.copyDepthFrom(main);
        main.copyDepthFrom(terrainDepth);
        main.bindWrite(false);
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (Entity entity : shown) {
            render(mc, entity, cam, partialTick, DEPTH);
            render(mc, entity, cam, partialTick, COLOUR);
        }
        main.copyDepthFrom(frameDepth);
        main.bindWrite(false);
    }

    private static void drawOver(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !UltraZones.isZone(mc.level)) return;
        Vec3 cam = context.camera().getPosition();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (Entity entity : shadows(mc)) {
            if (!behindWater(mc, cam, entity, true)) continue;
            render(mc, entity, cam, partialTick, DEPTH_OVER);
            render(mc, entity, cam, partialTick, COLOUR_OVER);
        }
    }

    private static List<Entity> shadows(Minecraft mc) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (WildsShadows.isShadow(entity)) found.add(entity);
        }
        return found;
    }

    private static TextureTarget sized(@Nullable TextureTarget target, RenderTarget main) {
        if (target == null) return new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (target.width != main.width || target.height != main.height) target.resize(main.width, main.height, Minecraft.ON_OSX);
        return target;
    }

    private static boolean behindWater(Minecraft mc, Vec3 cam, Entity entity, boolean clearOfBlocks) {
        AABB box = entity.getBoundingBox();
        Vec3 c = box.getCenter();
        double dx = box.getXsize() * INSET, dy = box.getYsize() * INSET, dz = box.getZsize() * INSET;
        boolean water = false;
        for (Vec3 p : List.of(c, c.add(0, dy, 0), c.add(dx, 0, 0), c.add(-dx, 0, 0), c.add(0, 0, dz), c.add(0, 0, -dz))) {
            if (clearOfBlocks && mc.level.clip(new ClipContext(cam, p, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty())).getType() != HitResult.Type.MISS) return false;
            water |= mc.level.clip(new ClipContext(cam, p, ClipContext.Block.VISUAL, ClipContext.Fluid.WATER,
                    CollisionContext.empty())).getType() != HitResult.Type.MISS;
        }
        return water;
    }

    private static void render(Minecraft mc, Entity entity, Vec3 cam, float partialTick, Map<RenderType, RenderType> pass) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cam.x;
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cam.y;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cam.z;
        boolean depth = pass == DEPTH || pass == DEPTH_OVER;
        boolean over = pass == DEPTH_OVER || pass == COLOUR_OVER;
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, x, y, z, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick, new PoseStack(),
                type -> BUFFERS.getBuffer(pass.computeIfAbsent(type, inner -> wrap(inner, depth, over))),
                dispatcher.getPackedLightCoords(entity, partialTick));
        dispatcher.setRenderShadow(mc.options.entityShadows().get());
        BUFFERS.endBatch();
    }

    private static RenderType wrap(RenderType inner, boolean depth, boolean over) {
        return new RenderType(inner + (depth ? " shadow depth" : " shadow colour") + (over ? " over" : ""), inner.format(),
                inner.mode(), inner.bufferSize(), false, false, () -> {
            inner.setupRenderState();
            RenderSystem.enableDepthTest();
            if (depth) {
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthFunc(over ? GL11.GL_ALWAYS : GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            } else {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, ALPHA);
                RenderSystem.depthFunc(GL11.GL_EQUAL);
                RenderSystem.depthMask(false);
            }
        }, () -> {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            inner.clearRenderState();
        }) {};
    }
}
