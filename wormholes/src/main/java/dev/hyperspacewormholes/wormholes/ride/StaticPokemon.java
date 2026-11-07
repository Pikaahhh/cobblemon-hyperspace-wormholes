package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * The still Pokemon of the mod's worlds, which are always shown out to the player's whole render distance: wild Pokemon
 * with no AI (an Ultra Beast at its zone's spot, the Megalopolis Necrozma, bosses, a Wilds shadow) or holding a statue's
 * pose (PlantXurkitrees.STATUE). The server tracks them out to its view distance (trackingRange, StatueTrackingMixin), and
 * the client draws them at any distance it has them (StaticPokemonRenderMixin, ZonePlayerVisibleMixin) and never lets a
 * culling mod drop them (EntityCullingMixin).
 */
public final class StaticPokemon {
    private StaticPokemon() {}

    public static boolean is(Entity entity) {
        return entity instanceof PokemonEntity pokemon && UltraDimensions.isUltra(pokemon.level())
                && pokemon.getOwnerUUID() == null
                && (pokemon.isNoAi() || pokemon.getEntityData().get(PokemonEntity.getASPECTS()).contains(PlantXurkitrees.STATUE));
    }

    public static int trackingRange(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        if (server == null || !is(entity)) return 0;
        return server.getPlayerList().getViewDistance() * 16;
    }
}
