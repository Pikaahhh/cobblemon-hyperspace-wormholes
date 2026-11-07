package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Function;

/**
 * Client side of the map icons: keeps the spatial distortions the server sends (MapDistortions) and works out which one
 * is under the mouse for the map hooks WorldMapIcons (Xaero's World Map), MinimapIcons (Xaero's Minimap) and
 * JourneyMapIcons (JourneyMap). Display only. None of the three map mods is needed: each hook is reached by reflection
 * or an optional @Pseudo mixin and does nothing without its mod.
 */
public final class MapIcons {
    public static final ResourceLocation DISTORTION = Hyperspace.id("textures/map/distortion.png");
    private static volatile List<MapDistortions.Mark> marks = List.of();

    private MapIcons() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MapDistortions.Marks.TYPE, (payload, context) -> marks = List.copyOf(payload.marks()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> marks = List.of());
        if (FabricLoader.getInstance().isModLoaded("journeymap")) ClientTickEvents.END_CLIENT_TICK.register(JourneyMapIcons::tick);
    }

    public static List<MapDistortions.Mark> marks(ResourceKey<Level> shown) {
        return Level.OVERWORLD.equals(shown) ? marks : List.of();
    }

    public static List<Component> tooltip(MapDistortions.Mark mark) {
        return List.of(Component.translatable("distortion.hyperspace_wormholes.label", Distortions.stars(mark.stars()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), Distortions.typeList(mark.types()));
    }

    public static MapDistortions.Mark under(double mouseX, double mouseY, double reach, ResourceKey<Level> shown, Function<BlockPos, double[]> screen) {
        MapDistortions.Mark best = null;
        double bestDistance = reach * reach;
        for (MapDistortions.Mark mark : marks(shown)) {
            double[] at = screen.apply(mark.pos());
            double distance = (at[0] - mouseX) * (at[0] - mouseX) + (at[1] - mouseY) * (at[1] - mouseY);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = mark;
            }
        }
        return best;
    }
}
