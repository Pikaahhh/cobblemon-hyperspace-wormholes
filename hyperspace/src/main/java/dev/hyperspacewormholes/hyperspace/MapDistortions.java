package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spatial distortions on the maps. Every second each player is sent the distortions the world keeps (about three around
 * each player, each for an hour), only when that list changed. The client draws each as a distortion icon with its stars
 * and types on Xaero's World Map, Xaero's Minimap and JourneyMap (MapIcons). Display only, no fast travel.
 */
public final class MapDistortions {
    private static final int SYNC_EVERY = 20;
    private static final Map<UUID, List<Mark>> SENT = new HashMap<>();

    public record Mark(BlockPos pos, int stars, List<String> types) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Mark::pos, ByteBufCodecs.VAR_INT, Mark::stars,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Mark::types, Mark::new);
    }

    public record Marks(List<Mark> marks) implements CustomPacketPayload {
        public static final Type<Marks> TYPE = new Type<>(Hyperspace.id("map_distortions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Marks> CODEC =
                StreamCodec.composite(Mark.CODEC.apply(ByteBufCodecs.list()), Marks::marks, Marks::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MapDistortions() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Marks.TYPE, Marks.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % SYNC_EVERY == 0) sync(server);
        });
    }

    private static void sync(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<Mark> marks = new ArrayList<>();
        for (Distortions.Distortion distortion : Distortions.all(server)) {
            if (distortion.fading || distortion.facing == null || distortion.ends <= now) continue;
            marks.add(new Mark(new BlockPos(distortion.x, distortion.y, distortion.z), distortion.stars, List.copyOf(distortion.types)));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, Marks.TYPE) || marks.equals(SENT.get(player.getUUID()))) continue;
            SENT.put(player.getUUID(), marks);
            ServerPlayNetworking.send(player, new Marks(marks));
        }
    }
}
