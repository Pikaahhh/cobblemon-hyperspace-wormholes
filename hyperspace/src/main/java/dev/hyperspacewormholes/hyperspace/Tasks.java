package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import kotlin.Unit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Trip tasks: the list of stuff to do each trip, tracking progress, EXP candy for each one (bigger with more stars),
 * and a golden ball (plus the Taskmaster advancement) when you finish the lot.
 */
public class Tasks {
    public enum Kind {
        DEFEAT("Defeat 3 Pokémon.", 3),
        CATCH("Catch 3 Pokémon.", 3),
        BALLS("Destroy 3 Hyperspace Poké Balls.", 3),
        QUICK("Quick battle and defeat 5 Pokémon.", 5),
        LEGENDARY("Catch or defeat the legendary Pokémon.", 1);

        final String text;
        final int goal;

        Kind(String text, int goal) {
            this.text = text;
            this.goal = goal;
        }
    }

    private static final List<Kind> COMMON = List.of(Kind.DEFEAT, Kind.CATCH, Kind.BALLS, Kind.QUICK);
    private static final int COUNT = 3;

    public record Line(String text, String reward, int progress, int goal) {
        public static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Line::text,
                ByteBufCodecs.STRING_UTF8, Line::reward, ByteBufCodecs.VAR_INT, Line::progress, ByteBufCodecs.VAR_INT, Line::goal, Line::new);

        public boolean done() {
            return progress >= goal;
        }
    }

    public record Sync(List<Line> tasks, List<Line> missions) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Hyperspace.id("tasks"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(Line.CODEC.apply(ByteBufCodecs.list()), Sync::tasks,
                Line.CODEC.apply(ByteBufCodecs.list()), Sync::missions, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.HIGH, event -> {
            ServerPlayer player = event.getPlayer();
            if (Hyperspace.inHyperspace(player)) {
                progress(player, Kind.CATCH);
                if (Spawns.isBoss(event.getPokemon())) progress(player, Kind.LEGENDARY);
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.LOW, event -> {
            BattlePokemon killed = event.getKilled();
            if (killed.getActor().getType() != ActorType.WILD) return Unit.INSTANCE;
            for (ServerPlayer player : event.getBattle().getPlayers()) {
                if (!Hyperspace.inHyperspace(player)) continue;
                progress(player, Kind.DEFEAT);
                if (Spawns.isBoss(killed.getEffectedPokemon())) progress(player, Kind.LEGENDARY);
                break;
            }
            return Unit.INSTANCE;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof PokemonEntity pokemon) || !(source.getEntity() instanceof ServerPlayer player)) return;
            Pokemon wild = pokemon.getPokemon();
            if (!wild.isWild() || HyperspaceLegendaries.faintedInBattle(wild) || !Hyperspace.inHyperspace(player)) return;
            progress(player, Kind.DEFEAT);
            if (Spawns.isBoss(wild)) progress(player, Kind.LEGENDARY);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
    }

    public static void quickDefeat(ServerPlayer player) {
        progress(player, Kind.QUICK);
        progress(player, Kind.DEFEAT);
    }

    public static void arrive(ServerPlayer player, WorldData.Trip trip) {
        MinecraftServer server = player.server;
        WorldData data = WorldData.get(server);
        CompoundTag board = data.tasks.computeIfAbsent(trip.island, island -> create(player.getRandom()));
        if (!board.contains("special") && trip.legendaryPending) {
            board.putInt("special", Kind.LEGENDARY.ordinal());
            board.putIntArray("kinds", new int[0]);
            board.putIntArray("progress", new int[0]);
        }
        data.setDirty();
        if (complete(board)) golden(player, trip.island, board);
        sendAll(server, trip.island);
    }

    public static void left(ServerPlayer player) {
        ServerPlayNetworking.send(player, new Sync(List.of(), List.of()));
    }

    public static void ended(MinecraftServer server, int island) {
        WorldData data = WorldData.get(server);
        if (data.trips.values().stream().anyMatch(trip -> trip.island == island) || data.tasks.remove(island) == null) return;
        data.setDirty();
        ServerLevel hyperspace = server.getLevel(Hyperspace.HYPERSPACE);
        if (hyperspace != null) FloatingBalls.clearGolden(hyperspace, island);
    }

    public static void progress(ServerPlayer player, Kind kind) {
        if (kind == Kind.BALLS) FloatingBalls.popping(player);
        int island = island(player);
        if (island < 0) return;
        MinecraftServer server = player.server;
        WorldData data = WorldData.get(server);
        CompoundTag board = data.tasks.get(island);
        if (board == null) return;
        int[] kinds = ints(board, "kinds");
        int[] progress = ints(board, "progress");
        boolean changed = false;
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != kind.ordinal() || progress[i] >= kind.goal) continue;
            progress[i]++;
            changed = true;
            if (progress[i] < kind.goal) continue;
            for (ServerPlayer member : members(server, island)) {
                give(member, HoopaStars.candy(member), 1);
                member.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6f, 1.2f);
            }
        }
        board.putIntArray("progress", progress);
        if (changed && complete(board)) {
            for (ServerPlayer member : members(server, island)) {
                Trips.notifySound(member, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
                golden(member, island, board);
                HyperspaceAdvancements.tasks(member);
            }
        }
        if (board.contains("special") && board.getInt("special") == kind.ordinal() && !board.getBoolean("specialDone")) {
            board.putBoolean("specialDone", true);
            changed = true;
            for (ServerPlayer member : members(server, island)) {
                Trips.notifySound(member, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
                if (kind != Kind.LEGENDARY) continue;
                give(member, Items.ENCHANTED_GOLDEN_APPLE, 1);
                give(member, HoopaStars.candy(member), 3);
            }
        }
        if (!changed) return;
        data.setDirty();
        sendAll(server, island);
    }

    private static CompoundTag create(RandomSource random) {
        List<Kind> pool = new ArrayList<>(COMMON);
        int[] kinds = new int[COUNT];
        for (int i = 0; i < COUNT; i++) kinds[i] = pool.remove(random.nextInt(pool.size())).ordinal();
        CompoundTag board = new CompoundTag();
        board.putIntArray("kinds", kinds);
        board.putIntArray("progress", new int[COUNT]);
        board.put("golden", new ListTag());
        return board;
    }

    private static boolean complete(CompoundTag board) {
        int[] kinds = ints(board, "kinds");
        int[] progress = ints(board, "progress");
        for (int i = 0; i < kinds.length; i++) {
            if (progress[i] < Kind.values()[kinds[i]].goal) return false;
        }
        return kinds.length > 0;
    }

    private static void golden(ServerPlayer player, int island, CompoundTag board) {
        ListTag given = board.getList("golden", Tag.TAG_INT_ARRAY);
        for (Tag tag : given) {
            if (NbtUtils.loadUUID(tag).equals(player.getUUID())) return;
        }
        ServerLevel hyperspace = player.server.getLevel(Hyperspace.HYPERSPACE);
        if (hyperspace == null || !FloatingBalls.golden(hyperspace, island, player.getUUID())) return;
        given.add(NbtUtils.createUUID(player.getUUID()));
        board.put("golden", given);
        WorldData.get(player.server).setDirty();
    }

    private static int island(ServerPlayer player) {
        WorldData.Trip trip = WorldData.get(player.server).trips.get(player.getUUID());
        return trip == null ? -1 : trip.island;
    }

    private static List<ServerPlayer> members(MinecraftServer server, int island) {
        return server.getPlayerList().getPlayers().stream().filter(player -> island(player) == island).toList();
    }

    private static void sendAll(MinecraftServer server, int island) {
        members(server, island).forEach(Tasks::send);
    }

    private static void send(ServerPlayer player) {
        int island = island(player);
        CompoundTag board = island < 0 ? null : WorldData.get(player.server).tasks.get(island);
        if (board == null) {
            left(player);
            return;
        }
        String candy = HoopaStars.candyName(player);
        List<Line> tasks = new ArrayList<>();
        int[] kinds = ints(board, "kinds");
        int[] progress = ints(board, "progress");
        for (int i = 0; i < kinds.length; i++) {
            Kind kind = Kind.values()[kinds[i]];
            tasks.add(new Line(kind.text, candy + " x1", Math.min(progress[i], kind.goal), kind.goal));
        }
        List<Line> missions = new ArrayList<>();
        if (board.contains("special")) {
            Kind kind = Kind.values()[board.getInt("special")];
            missions.add(new Line(kind.text, "Enchanted Golden Apple x1, " + candy + " x3", board.getBoolean("specialDone") ? 1 : 0, 1));
        }
        ServerPlayNetworking.send(player, new Sync(tasks, missions));
    }

    private static int[] ints(CompoundTag board, String key) {
        return board.getIntArray(key);
    }

    private static void give(ServerPlayer player, Item item, int count) {
        while (count > 0) {
            ItemStack stack = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
            count -= stack.getCount();
            give(player, stack);
        }
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
