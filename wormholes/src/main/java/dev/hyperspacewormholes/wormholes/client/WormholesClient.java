package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.Packets;
import dev.hyperspacewormholes.wormholes.GameplayConfig;
import dev.hyperspacewormholes.wormholes.CosmicJar;
import dev.hyperspacewormholes.wormholes.LuminescentIce;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.render.RenderStages;
import dev.hyperspacewormholes.wormholes.client.render.LodestoneLabelRenderer;
import dev.hyperspacewormholes.wormholes.client.render.TunnelRenderer;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import dev.hyperspacewormholes.wormholes.client.render.WarpRenderTypes;
import dev.hyperspacewormholes.wormholes.client.render.WormholeEntityRenderer;
import dev.hyperspacewormholes.wormholes.lodestone.Lodestones;
import dev.hyperspacewormholes.wormholes.network.BossHudPayload;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import dev.hyperspacewormholes.wormholes.network.BossTransitionPayload;
import dev.hyperspacewormholes.wormholes.network.RideEndPayload;
import dev.hyperspacewormholes.wormholes.network.RideFollowPayload;
import dev.hyperspacewormholes.wormholes.network.RideMusicPayload;
import dev.hyperspacewormholes.wormholes.network.RideStartPayload;
import dev.hyperspacewormholes.wormholes.network.RiderEmergePayload;
import dev.hyperspacewormholes.wormholes.network.RoarPayload;
import dev.hyperspacewormholes.wormholes.network.UltraBurstPayload;
import dev.hyperspacewormholes.wormholes.network.WarpFlashPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.function.BiConsumer;

/**
 * Client entry point for the wormholes half. Hooks up renderers, dimension effects, keybinds, packet receivers, the HUD and
 * render hooks, the Ultra worlds' own music and skies, and Luminescent Ice's tint and gradient. The Ultra Jungle keeps the
 * overworld's sky with the clouds at y 260, over the tallest swole trees; the warp, Deep Sea, Cliff World, Plant, Desert,
 * Megalopolis and Ruin have none of vanilla's. Also the Megalopolis fight's scenes (Cinematics) and Light That Burns the Sky's
 * animation (BurnsSkyClient).
 */
public class WormholesClient {
    public static void init(IEventBus modBus) {
        Packets.onClient(GameplayConfig.Sync.TYPE, payload -> GameplayConfig.receive(payload));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> GameplayConfig.forgetServer());
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerEntityRenderer(Wormholes.WORMHOLE.get(), WormholeEntityRenderer::new);
                    event.registerBlockEntityRenderer(Lodestones.BLOCK_ENTITY.get(), LodestoneLabelRenderer::new);
                });
        modBus.addListener(RegisterColorHandlersEvent.Block.class, event -> event.register(
                (state, level, pos, tint) -> LuminescentIce.colour(pos == null ? 64 : pos.getY()), ice()));
        modBus.addListener(RegisterColorHandlersEvent.Item.class,
                event -> event.register((stack, tint) -> LuminescentIce.colour(70), LuminescentIce.ITEM.get()));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            for (Block block : ice()) ItemBlockRenderTypes.setRenderLayer(block, RenderType.translucent());
            ItemProperties.register(CosmicJar.ITEM.get(), Wormholes.id("energy"),
                    (stack, level, entity, seed) -> CosmicJar.energy(stack) / (float) CosmicJar.CAPACITY);
        }));
        LuminescentIceModel.register(modBus);

        modBus.addListener(RegisterDimensionSpecialEffectsEvent.class, event -> {
            event.register(Wormholes.id("ultra_warp"), new UltraWarpEffects());
            event.register(Wormholes.id("ultra_jungle"), new UltraJungleEffects());
            event.register(Wormholes.id("ultra_deep_sea"), new CloudlessEffects(null));
            event.register(Wormholes.id("ultra_wilds_cliff"), new CloudlessEffects(null));
            event.register(Wormholes.id("ultra_plant"), new CloudlessEffects(UltraPlantSky::render));
            event.register(Wormholes.id("ultra_desert"), new CloudlessEffects(null));
            event.register(Wormholes.id("ultra_megalopolis"),
                    new CloudlessEffects((level, view) -> UltraMegalopolisSky.render(view)));
            event.register(Wormholes.id("ultra_ruin"), new UltraRuinEffects());
        });
        RenderStages.afterEntities(TunnelRenderer::renderWorld);
        RenderStages.afterTranslucent(TunnelRenderer::renderAfterWater);
        RenderStages.afterEntities(SkyClouds::render);
        RenderStages.start(event -> WormholeEntityRenderer.clearQueued());
        RenderStages.afterTranslucent(WormholeEntityRenderer::drawAfterWater);
        RenderStages.last(WormholeEntityRenderer::drawLast);
        RenderStages.start(ShadowThroughWater::keepDrawn);
        RenderStages.beforeEntities(ShadowThroughWater::saveTerrainDepth);
        RenderStages.afterTranslucent(ShadowThroughWater::drawAfterWater);
        RenderStages.last(ShadowThroughWater::drawLast);
        RenderStages.afterEntities(LandingShadow::render);
        onHud(RideHud::render);

        Packets.onClient(RideStartPayload.TYPE, p -> RideClient.start(p));
        Packets.onClient(RideEndPayload.TYPE, p -> RideClient.end());
        Packets.onClient(RideFollowPayload.TYPE, p -> RideClient.onFollow(p));
        Packets.onClient(WarpFlashPayload.TYPE, p -> RideClient.whiteOut(p.ticks()));
        Packets.onClient(RideMusicPayload.TYPE, p -> UltraWormholeMusic.start());
        Packets.onClient(RoarPayload.TYPE, p -> RoarClient.startRoar(p.entityId()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RideClient.tick(Minecraft.getInstance()));
        Packets.onClient(RiderEmergePayload.TYPE, p -> RiderArrivalClient.start(p));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RiderArrivalClient.tick(Minecraft.getInstance()));
        Packets.onClient(UltraBurstPayload.TYPE, p -> UltraBurstScene.start(p));
        UltraBurstScene.register();
        Packets.onClient(BossTransitionPayload.TYPE, p -> BossTransition.start(p));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> BossTransition.tick(Minecraft.getInstance()));
        WarpRenderTypes.register(modBus);
        BurnsSkyClient.register();
        BossClient.register();
        Packets.onClient(BossHudPayload.TYPE, p -> BossHud.receive(p));
        BossHud.register();
        Cinematics.register();
        Packets.onClient(NecrozmaScenePayload.TYPE, p -> Cinematics.receive(p));

        UltraWorldMusic.register();
        NecrozmaWeakened.register();
        UltraPlantAmbience.register();
        StatueMeshes.register();
        RoarClient.registerKey(modBus);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RoarClient.tick(Minecraft.getInstance()));

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            RideClient.reset();
            RiderArrivalClient.reset();
        });
    }

    private static Block[] ice() {
        return LuminescentIce.family().toArray(Block[]::new);
    }

    /** Draws on top of the whole HUD: after the game's own layers, with the depth test off as it is once they're done. */
    public static void onHud(BiConsumer<GuiGraphics, DeltaTracker> draw) {
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, event -> {
            RenderSystem.disableDepthTest();
            draw.accept(event.getGuiGraphics(), event.getPartialTick());
            RenderSystem.enableDepthTest();
        });
    }

    private static final class UltraWarpEffects extends DimensionSpecialEffects {
        private static final Vec3 BACKDROP = new Vec3(0.02, 0.01, 0.06);

        UltraWarpEffects() {
            super(Float.NaN, false, SkyType.NONE, true, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return BACKDROP;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTick) {
            return null;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                 Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            setupFog.run();
            TunnelRenderer.renderSky(modelViewMatrix, camera, partialTick);
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX,
                                    double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
                                         double camX, double camY, double camZ) {
            return true;
        }
    }

    /** The Ultra Ruin's haze: its sky is nothing but the biome's fog colour, which no hour dims and no sunset tints. */
    private static final class UltraRuinEffects extends DimensionSpecialEffects {
        UltraRuinEffects() {
            super(Float.NaN, true, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTick) {
            return null;
        }
    }

    private static final class UltraJungleEffects extends DimensionSpecialEffects {
        UltraJungleEffects() {
            super(260f, true, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.multiply(brightness * 0.94f + 0.06f, brightness * 0.94f + 0.06f, brightness * 0.91f + 0.09f);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }

    /** Overworld effects with no clouds, and the world's own sky in place of vanilla's when it has one. */
    private static final class CloudlessEffects extends DimensionSpecialEffects.OverworldEffects {
        private final @Nullable BiConsumer<ClientLevel, Matrix4f> sky;

        CloudlessEffects(@Nullable BiConsumer<ClientLevel, Matrix4f> sky) {
            this.sky = sky;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                 Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            if (sky == null) return false;
            setupFog.run();
            sky.accept(level, modelViewMatrix);
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX,
                                    double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
            return true;
        }
    }
}
