package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.tooltips.TooltipGenerator;
import com.cobblemon.mod.common.client.tooltips.TooltipManager;
import java.util.List;
import net.minecraft.world.item.ItemStack;
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
 * Client entry point for the Hyperspace half. Hooks up renderers, the sky, the greyscale effect, music, HUDs,
 * quick-battle targeting, the donut model and tooltips, Hoopa's donut-feeding motions and the map icons.
 */
public class HyperspaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HyperspaceMusic.register();
        HyperspaceSky.register();
        HyperspaceGrey.register();
        HyperspaceEdge.register();
        IrisOff.register();
        QuickBattleTargeting.register();
        TasksHud.register();
        HoopaFeedClient.register();
        HoopaClip.register();
        HoopaTracks.register();
        HoopaGlitch.register();
        MapIcons.register();
        EntityRendererRegistry.register(FloatingBalls.TYPE, FloatingBallRenderer::new);
        EntityRendererRegistry.register(FloatingBalls.GOLDEN, FloatingBallRenderer::new);
        EntityRendererRegistry.register(Rift.TYPE, RiftRenderer::new);
        FloatingBall.clientDelegate = BallPose::new;
        FloatingBall.seen = ball -> ball.belongsTo(Minecraft.getInstance().player);
        ClientPlayNetworking.registerGlobalReceiver(OtherPlayersLegendaries.Payload.TYPE, (payload, context) -> {
            OtherPlayersLegendaries.hidden.clear();
            OtherPlayersLegendaries.hidden.addAll(payload.ids());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> OtherPlayersLegendaries.hidden.clear());
        TooltipManager.INSTANCE.registerTooltipGenerator(new TooltipGenerator() {
            @Override
            public List<Component> generateAdditionalTooltip(ItemStack stack, List<Component> lines) {
                return DonutBoosts.berryInfo(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (ModItems.HYPERSPACE_BERRIES.containsValue(stack.getItem())) {
                String berry = id.getPath().substring("hyperspace_".length());
                lines.add(Math.min(1, lines.size()), Component.translatable("item.cobblemon." + berry + ".tooltip").withStyle(ChatFormatting.GRAY));
            }
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
