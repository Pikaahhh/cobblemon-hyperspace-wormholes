package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.settings.ServerSettings;
import com.cobblemon.mod.common.client.tooltips.TooltipGenerator;
import com.cobblemon.mod.common.client.tooltips.TooltipManager;
import dev.hyperspacewormholes.Packets;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client entry point for the Hyperspace half. Hooks up renderers, the sky, the greyscale effect, music, HUDs,
 * quick-battle targeting, the donut model and tooltips, Hoopa's donut-feeding motions and the map icons.
 */
public class HyperspaceClient {
    public static void init(IEventBus modBus) {
        HyperspaceMusic.register();
        HyperspaceSky.register(modBus);
        HyperspaceGrey.register(modBus);
        HyperspaceEdge.register(modBus);
        IrisOff.register();
        QuickBattleTargeting.register(modBus);
        TasksHud.register();
        HoopaFeedClient.register();
        HoopaClip.register();
        HoopaTracks.register();
        HoopaGlitch.register();
        MapIcons.register();
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(FloatingBalls.TYPE.get(), FloatingBallRenderer::new);
            event.registerEntityRenderer(FloatingBalls.GOLDEN.get(), FloatingBallRenderer::new);
            event.registerEntityRenderer(Rift.TYPE.get(), RiftRenderer::new);
        });
        FloatingBall.clientDelegate = BallPose::new;
        FloatingBall.seen = ball -> ball.belongsTo(Minecraft.getInstance().player);
        Packets.onClient(OtherPlayersLegendaries.Payload.TYPE, payload -> {
            OtherPlayersLegendaries.hidden.clear();
            OtherPlayersLegendaries.hidden.addAll(payload.ids());
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> OtherPlayersLegendaries.hidden.clear());
        TooltipManager.INSTANCE.registerTooltipGenerator(new TooltipGenerator() {
            @Override
            public List<Component> generateAdditionalTooltip(ItemStack stack, List<Component> lines) {
                return DonutBoosts.berryInfo(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        });
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> {
            ItemStack stack = event.getItemStack();
            List<Component> lines = event.getToolTip();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (ModItems.HYPERSPACE_BERRIES.containsValue(stack.getItem())) {
                String berry = id.getPath().substring("hyperspace_".length());
                lines.add(Math.min(1, lines.size()), Component.translatable("item.cobblemon." + berry + ".tooltip").withStyle(ChatFormatting.GRAY));
            }
        });
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ItemProperties.register(ModItems.DONUT.get(), ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "icing"),
                    (stack, level, entity, seed) -> DonutItem.icing(stack) / 100f);
            ItemProperties.register(ModItems.DONUT.get(), ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "sprinkles"),
                    (stack, level, entity, seed) -> DonutItem.sprinkles(stack) >= 0 ? 1f : 0f);
        }));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register((stack, layer) -> {
            int sprinkles = DonutItem.sprinkles(stack);
            int sprinkleLayer = DonutItem.icing(stack) == 0 ? 1 : 2;
            return sprinkles >= 0 && layer == sprinkleLayer ? FastColor.ARGB32.opaque(sprinkles) : -1;
        }, ModItems.DONUT.get()));
    }
}
