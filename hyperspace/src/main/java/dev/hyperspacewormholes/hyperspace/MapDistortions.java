package dev.hyperspacewormholes.hyperspace;

import dev.hyperspacewormholes.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
        Packets.toClient(Marks.TYPE, Marks.CODEC);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % SYNC_EVERY == 0) sync(event.getServer());
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
            if (!player.connection.hasChannel(Marks.TYPE) || marks.equals(SENT.get(player.getUUID()))) continue;
            SENT.put(player.getUUID(), marks);
            PacketDistributor.sendToPlayer(player, new Marks(marks));
        }
    }
}
