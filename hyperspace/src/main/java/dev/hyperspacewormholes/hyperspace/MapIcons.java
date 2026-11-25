package dev.hyperspacewormholes.hyperspace;

import dev.hyperspacewormholes.Packets;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
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
        Packets.onClient(MapDistortions.Marks.TYPE, payload -> marks = List.copyOf(payload.marks()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> marks = List.of());
        if (ModList.get().isLoaded("journeymap")) NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> JourneyMapIcons.tick(Minecraft.getInstance()));
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
