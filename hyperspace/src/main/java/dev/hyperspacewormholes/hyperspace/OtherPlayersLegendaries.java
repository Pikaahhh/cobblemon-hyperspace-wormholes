package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides other people's bosses from you in Hyperspace. The server tells each client which bosses aren't theirs, and
 * those don't get drawn and can't be clicked.
 */
public class OtherPlayersLegendaries {
    public static final Set<Integer> hidden = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, List<Integer>> SENT = new HashMap<>();
    public record Payload(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Hyperspace.id("hidden_bosses"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Payload::ids, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
    }

    public static void sync(ServerLevel level) {
        List<PokemonEntity> bosses = new ArrayList<>();
        for (PokemonEntity entity : Spawns.wild(level)) {
            if (Spawns.isBoss(entity.getPokemon()) && Spawns.owner(entity.getPokemon()) != null) bosses.add(entity);
        }
        for (ServerPlayer player : level.players()) {
            if (!ServerPlayNetworking.canSend(player, Payload.TYPE)) continue;
            List<Integer> ids = new ArrayList<>();
            for (PokemonEntity boss : bosses) {
                if (!player.getUUID().equals(Spawns.owner(boss.getPokemon()))) ids.add(boss.getId());
            }
            if (ids.equals(SENT.get(player.getUUID()))) continue;
            SENT.put(player.getUUID(), ids);
            ServerPlayNetworking.send(player, new Payload(ids));
        }
    }

    public static boolean hides(Entity entity) {
        return !hidden.isEmpty() && entity.level().isClientSide() && entity.level().dimension().equals(Hyperspace.HYPERSPACE)
                && hidden.contains(entity.getId());
    }
}
