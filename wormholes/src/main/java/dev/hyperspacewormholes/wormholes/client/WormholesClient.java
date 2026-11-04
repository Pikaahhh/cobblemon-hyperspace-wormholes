package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.CosmicJar;
import dev.hyperspacewormholes.wormholes.LuminescentIce;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.render.TunnelRenderer;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import dev.hyperspacewormholes.wormholes.client.render.WarpRenderTypes;
import dev.hyperspacewormholes.wormholes.client.render.WormholeEntityRenderer;
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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Client entry point for the wormholes half. Hooks up renderers, dimension effects, keybinds, packet receivers, the HUD and
 * render hooks, the Ultra worlds' own music and skies, and Luminescent Ice's tint and gradient. The Ultra Jungle keeps the
 * overworld's sky with the clouds at y 260, over the tallest swole trees; the warp, Deep Sea, Cliff World, Plant, Desert and
 * Megalopolis have none of vanilla's. Also the Megalopolis fight's scenes (Cinematics) and Light That Burns the Sky's
 * animation (BurnsSkyClient).
 */
public class WormholesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Wormholes.WORMHOLE, WormholeEntityRenderer::new);
        Block[] ice = LuminescentIce.family().toArray(Block[]::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), ice);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> LuminescentIce.colour(pos == null ? 64 : pos.getY()),
                ice);
        ColorProviderRegistry.ITEM.register((stack, tint) -> LuminescentIce.colour(70), LuminescentIce.ITEM);
        LuminescentIceModel.register();
        ItemProperties.register(CosmicJar.ITEM, Wormholes.id("energy"),
                (stack, level, entity, seed) -> CosmicJar.energy(stack) / (float) CosmicJar.CAPACITY);

        DimensionRenderingRegistry.registerDimensionEffects(Wormholes.id("ultra_warp"), new UltraWarpEffects());
        DimensionRenderingRegistry.registerDimensionEffects(Wormholes.id("ultra_jungle"), new UltraJungleEffects());
        DimensionRenderingRegistry.registerSkyRenderer(Wormholes.ULTRA_WARP, TunnelRenderer::renderSky);
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_WARP, context -> {});
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_DEEP_SEA, context -> {});
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.WILDS_CLIFF, context -> {});
        DimensionRenderingRegistry.registerSkyRenderer(Wormholes.ULTRA_PLANT, UltraPlantSky::render);
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_PLANT, context -> {});
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_DESERT, context -> {});
        DimensionRenderingRegistry.registerSkyRenderer(Wormholes.ULTRA_MEGALOPOLIS, UltraMegalopolisSky::render);
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_MEGALOPOLIS, context -> {});
        DimensionRenderingRegistry.registerWeatherRenderer(Wormholes.ULTRA_WARP, context -> {});
        WorldRenderEvents.AFTER_ENTITIES.register(TunnelRenderer::renderWorld);
        WorldRenderEvents.AFTER_ENTITIES.register(SkyClouds::render);
        WorldRenderEvents.START.register(context -> WormholeEntityRenderer.clearQueued());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WormholeEntityRenderer::drawAfterWater);
        WorldRenderEvents.LAST.register(WormholeEntityRenderer::drawLast);
        WorldRenderEvents.START.register(ShadowThroughWater::keepDrawn);
        WorldRenderEvents.BEFORE_ENTITIES.register(ShadowThroughWater::saveTerrainDepth);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ShadowThroughWater::drawAfterWater);
        WorldRenderEvents.LAST.register(ShadowThroughWater::drawLast);
        WorldRenderEvents.AFTER_ENTITIES.register(LandingShadow::render);
        HudRenderCallback.EVENT.register(RideHud::render);

        ClientPlayNetworking.registerGlobalReceiver(RideStartPayload.TYPE, (p, c) -> RideClient.start(p));
        ClientPlayNetworking.registerGlobalReceiver(RideEndPayload.TYPE, (p, c) -> RideClient.end());
        ClientPlayNetworking.registerGlobalReceiver(RideFollowPayload.TYPE, (p, c) -> RideClient.onFollow(p));
        ClientPlayNetworking.registerGlobalReceiver(WarpFlashPayload.TYPE, (p, c) -> RideClient.whiteOut(p.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(RideMusicPayload.TYPE, (p, c) -> UltraWormholeMusic.start());
        ClientPlayNetworking.registerGlobalReceiver(RoarPayload.TYPE, (p, c) -> RoarClient.startRoar(p.entityId()));
        ClientTickEvents.END_CLIENT_TICK.register(RideClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(RiderEmergePayload.TYPE, (p, c) -> RiderArrivalClient.start(p));
        ClientTickEvents.END_CLIENT_TICK.register(RiderArrivalClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(UltraBurstPayload.TYPE, (p, c) -> UltraBurstScene.start(p));
        UltraBurstScene.register();
        ClientPlayNetworking.registerGlobalReceiver(BossTransitionPayload.TYPE, (p, c) -> BossTransition.start(p));
        ClientTickEvents.END_CLIENT_TICK.register(BossTransition::tick);
        WarpRenderTypes.register();
        BurnsSkyClient.register();
        BossClient.register();
        ClientPlayNetworking.registerGlobalReceiver(BossHudPayload.TYPE, (p, c) -> BossHud.receive(p));
        BossHud.register();
        Cinematics.register();
        ClientPlayNetworking.registerGlobalReceiver(NecrozmaScenePayload.TYPE, (p, c) -> Cinematics.receive(p));

        UltraWorldMusic.register();
        NecrozmaWeakened.register();
        UltraPlantAmbience.register();
        StatueMeshes.register();
        RoarClient.registerKey();
        ClientTickEvents.END_CLIENT_TICK.register(RoarClient::tick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RideClient.reset();
            RiderArrivalClient.reset();
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
}
