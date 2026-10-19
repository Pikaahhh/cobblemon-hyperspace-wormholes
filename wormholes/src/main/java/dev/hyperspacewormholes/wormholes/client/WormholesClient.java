package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.CosmicJar;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.render.TunnelRenderer;
import dev.hyperspacewormholes.wormholes.client.render.WormholeEntityRenderer;
import dev.hyperspacewormholes.wormholes.network.RideEndPayload;
import dev.hyperspacewormholes.wormholes.network.RideFollowPayload;
import dev.hyperspacewormholes.wormholes.network.RideMusicPayload;
import dev.hyperspacewormholes.wormholes.network.RideStartPayload;
import dev.hyperspacewormholes.wormholes.network.RiderEmergePayload;
import dev.hyperspacewormholes.wormholes.network.RoarPayload;
import dev.hyperspacewormholes.wormholes.network.WarpFlashPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Client entry point for the wormholes half. Hooks up renderers, dimension effects, keybinds, packet receivers, the
 * HUD and render hooks.
 */
public class WormholesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Wormholes.WORMHOLE, WormholeEntityRenderer::new);
        ItemProperties.register(CosmicJar.ITEM, Wormholes.id("energy"),
                (stack, level, entity, seed) -> CosmicJar.energy(stack) / (float) CosmicJar.CAPACITY);

        DimensionRenderingRegistry.registerDimensionEffects(Wormholes.id("ultra_warp"), new UltraWarpEffects());
        DimensionRenderingRegistry.registerSkyRenderer(Wormholes.ULTRA_WARP, TunnelRenderer::renderSky);
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_WARP, context -> {});
        DimensionRenderingRegistry.registerCloudRenderer(Wormholes.ULTRA_DEEP_SEA, context -> {});
        DimensionRenderingRegistry.registerWeatherRenderer(Wormholes.ULTRA_WARP, context -> {});
        WorldRenderEvents.AFTER_ENTITIES.register(TunnelRenderer::renderWorld);
        WorldRenderEvents.START.register(context -> WormholeEntityRenderer.clearQueued());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WormholeEntityRenderer::drawAfterWater);
        WorldRenderEvents.LAST.register(WormholeEntityRenderer::drawLast);
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
}
