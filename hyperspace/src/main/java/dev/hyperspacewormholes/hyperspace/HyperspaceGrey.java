package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import dev.hyperspacewormholes.hyperspace.mixin.PostChainAccessor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;

/**
 * Makes Hyperspace grey. The world gets drawn in greyscale, then Pokemon, trainers, particles and anything else that
 * should keep its colour gets drawn back over it in colour.
 */
public class HyperspaceGrey {
    private static final ResourceLocation CHAIN = Hyperspace.id("shaders/post/greyscale.json");

    private static PostChain chain;
    private static boolean drawing;
    private static TextureTarget depth;
    private static int width = -1, height;
    private static boolean failed;

    public static void register() {
        WorldRenderEvents.LAST.register(HyperspaceGrey::render);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return Hyperspace.id("greyscale");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                if (chain != null) chain.close();
                chain = null;
                failed = false;
                width = -1;
            }
        });
    }

    private static void render(WorldRenderContext context) {
        HyperspaceEdge.prepare(context);
        ClientLevel level = context.world();
        if (level == null || !level.dimension().equals(Hyperspace.HYPERSPACE)) return;
        Minecraft client = Minecraft.getInstance();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        greyscale(client, context, partial);
        HyperspaceEdge.render(context);
        recolour(context, client, level, partial);
    }

    private static void greyscale(Minecraft client, WorldRenderContext context, float partial) {
        RenderTarget main = client.getMainRenderTarget();
        if (chain == null && !failed) {
            try {
                chain = new PostChain(client.getTextureManager(), client.getResourceManager(), main, CHAIN);
            } catch (Exception e) {
                failed = true;
                Hyperspace.LOGGER.error("Couldn't load the Hyperspace greyscale effect", e);
            }
        }
        if (chain == null) return;
        if (main.width != width || main.height != height) {
            chain.resize(main.width, main.height);
            width = main.width;
            height = main.height;
        }
        if (depth == null) depth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (depth.width != main.width || depth.height != main.height) depth.resize(main.width, main.height, Minecraft.ON_OSX);
        depth.copyDepthFrom(main);
        for (PostPass pass : ((PostChainAccessor) chain).hyperspace_wormholes$passes()) HyperspaceEdge.uniforms(pass.getEffect(), context);
        chain.process(partial);
        main.copyDepthFrom(depth);
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
    }

    public static boolean hidden(Entity entity) {
        if (drawing || chain == null || !entity.level().dimension().equals(Hyperspace.HYPERSPACE)) return false;
        return coloured(entity, Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    public static boolean holdParticles() {
        Minecraft client = Minecraft.getInstance();
        return chain != null && !drawing && client.level != null && client.level.dimension().equals(Hyperspace.HYPERSPACE);
    }

    private static boolean coloured(Entity entity, Camera camera) {
        if (entity instanceof Display.BlockDisplay || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) return false;
        if (entity == camera.getEntity() && !camera.isDetached()) return false;
        if (!(entity instanceof Display.ItemDisplay display)) return true;
        Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
        if (state == null) return false;
        // the thrown ring is its own item, and stays gold like the portal it becomes
        return state.itemStack().is(ModItems.HOOPA_PORTAL) || state.itemStack().is(ModItems.HOOPA_PORTAL_CLOSED) || state.itemStack().is(ModItems.HOOPA_THROWN_RING);
    }

    private static void recolour(WorldRenderContext context, Minecraft client, ClientLevel level, float partial) {
        Vec3 cam = context.camera().getPosition();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();

        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        client.gameRenderer.lightTexture().turnOnLightLayer();
        drawing = true;
        for (Entity entity : level.entitiesForRendering()) {
            if (!coloured(entity, context.camera())) continue;
            if (context.frustum() != null && !dispatcher.shouldRender(entity, context.frustum(), cam.x, cam.y, cam.z)) continue;
            double x = Mth.lerp(partial, entity.xOld, entity.getX()) - cam.x;
            double y = Mth.lerp(partial, entity.yOld, entity.getY()) - cam.y;
            double z = Mth.lerp(partial, entity.zOld, entity.getZ()) - cam.z;
            float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
            dispatcher.render(entity, x, y, z, yaw, partial, pose, buffers, dispatcher.getPackedLightCoords(entity, partial));
        }
        buffers.endBatch();
        client.particleEngine.render(client.gameRenderer.lightTexture(), context.camera(), partial);
        drawing = false;
        client.gameRenderer.lightTexture().turnOffLightLayer();
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
