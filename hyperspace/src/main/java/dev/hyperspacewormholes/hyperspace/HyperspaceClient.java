package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Client entry point for the Hyperspace half. Hooks up renderers, the sky, music, HUDs, quick-battle targeting, item
 * properties and tooltips.
 */
public class HyperspaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HyperspaceMusic.register();
        HyperspaceSky.register();
        HyperspaceGrey.register();
        IrisOff.register();
        SlimTrainers.register();
        QuickBattleTargeting.register();
        TasksHud.register();
        EntityRendererRegistry.register(FloatingBalls.TYPE, FloatingBallRenderer::new);
        EntityRendererRegistry.register(FloatingBalls.GOLDEN, FloatingBallRenderer::new);
        FloatingBall.clientDelegate = BallPose::new;
        FloatingBall.seen = ball -> ball.belongsTo(Minecraft.getInstance().player);
        ClientPlayNetworking.registerGlobalReceiver(OtherPlayersLegendaries.Payload.TYPE, (payload, context) -> {
            OtherPlayersLegendaries.hidden.clear();
            OtherPlayersLegendaries.hidden.addAll(payload.ids());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> OtherPlayersLegendaries.hidden.clear());
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (ModItems.HYPERSPACE_BERRIES.containsValue(stack.getItem())) {
                String berry = id.getPath().substring("hyperspace_".length());
                lines.add(Math.min(1, lines.size()), Component.translatable("item.cobblemon." + berry + ".tooltip").withStyle(ChatFormatting.GRAY));
            }
            Component lore = DonutBoosts.seasoningLore(id.toString());
            if (lore != null) lines.add(lore);
        });
        ItemProperties.register(ModItems.DONUT, ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "icing"),
                (stack, level, entity, seed) -> DonutItem.icing(stack) / 100f);
        ItemProperties.register(ModItems.DONUT, ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "sprinkles"),
                (stack, level, entity, seed) -> DonutItem.sprinkles(stack) >= 0 ? 1f : 0f);
        ColorProviderRegistry.ITEM.register((stack, layer) -> {
            int sprinkles = DonutItem.sprinkles(stack);
            int sprinkleLayer = DonutItem.icing(stack) == 0 ? 1 : 2;
            return sprinkles >= 0 && layer == sprinkleLayer ? FastColor.ARGB32.opaque(sprinkles) : -1;
        }, ModItems.DONUT);
    }
}
