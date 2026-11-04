package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.hyperspacewormholes.hyperspace.mixin.DisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.ItemDisplayAccessor;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.boss.Boss;
import dev.hyperspacewormholes.wormholes.boss.BossConfig;
import dev.hyperspacewormholes.wormholes.boss.BossDef;
import dev.hyperspacewormholes.wormholes.boss.BossDefs;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import dev.hyperspacewormholes.wormholes.boss.LegendWave;
import dev.hyperspacewormholes.wormholes.network.BossTransitionPayload;
import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Hoopa Unbound event, started when a player enters a distortion through a Distortion Splicer on the arena.
 * The trip seems normal at first, then the clock and task list turn to glitch text, the HUD goes, and Hoopa comes down through a sky ring for a multiplayer boss fight.
 * At 80% and 40% HP it throws rings and legendaries walk out to fight in its place; once it is beaten everyone gets a Confined Hoopa to catch and the way home opens.
 */
public final class HoopaEvent {
    private static final ResourceLocation DEF = Wormholes.id("hoopa_unbound");
    private static final ResourceLocation BOTTLE = ResourceLocation.fromNamespaceAndPath("mega_showdown", "prison_bottle");
    private static final String TAG = "hyperspace_wormholes_hoopa_event";
    private static final String CLIPS = "hoopa_unbound_hyperspace/";

    /** Ticks: seeming normal, the glitch, the nothing after it; the sky ring growing and the wait before Hoopa comes down. */
    private static final int NORMAL = 200, GLITCH = 60, BLANK = 60, ARRIVE = NORMAL + GLITCH + BLANK;
    private static final int RING_AT = 12, GROW = 30, DESCEND_AT = RING_AT + GROW + 12, REVEAL = 4;
    /** The clips: descend ends standing; ascend's last ticks are far up (hidden by then); catch2/3 end on the idle. */
    private static final int DESCEND = 80, ASCEND = 58, ASCEND_HIDE = 54, ASCEND_CUT = 21, CATCH = 20, HOLD_EARLY = 3;
    /** Model px: how high the sky ring is over Hoopa's feet (descend starts 160 up, ascend ends 200 up) and how wide
     *  its inside is (about Hoopa Unbound's arm span plus room). */
    private static final double RING_UP = 160, RING_INSIDE = 96, RING_INNER_UNITS = 36.4 / 16;
    /** Ticks: when in a descent the sky ring starts to close (the clip's pop comes as it gets home), its closing and
     *  flight home, and its flight up and opening. */
    private static final int RING_CLOSE = 8, RING_HOME = 12, RING_CAUGHT = 70, RING_LEAVES = RING_CAUGHT - RING_HOME - RING_CLOSE,
            RING_TOSS = 9, RING_THROW = 9, RING_OPEN = 9;
    /** The low right wrist as the ascend clip lets the ring go and as the descend clip catches it. */
    private static final HoopaSummon.Wrist TOSSED = new HoopaSummon.Wrist(new double[]{-45.99, 43.85, 12.40},
            new double[]{-0.555, 0.790, 0.262}, new float[]{-0.5443f, -0.2700f, 0.1202f, 0.7851f});
    private static final HoopaSummon.Wrist CAUGHT = new HoopaSummon.Wrist(new double[]{-44.67, 36.53, 19.12},
            new double[]{-0.559, 0.532, 0.636}, new float[]{-0.3438f, -0.2524f, 0.1597f, 0.8903f});
    private static final float THROWN_RING_PX = 8;
    private static final double UNSEEN_UP = 100;
    private static final int UNSEEN_AGAIN = 400;
    /** The rings: blocks to each side of Hoopa's spot, and in front of it for the third; how far it moves away for them. */
    private static final double LEGEND_FRONT = 7, RING_GAP = 2, MOVE_BACK = 6, MOVE_UP = 4, HOOPA_BACK = 14;
    private static final int MOVE_TICKS = 36;
    /** The seats (Necrozma's, wider for Hoopa's size): blocks in front of it for trainers and Pokemon, across by party. */
    private static final double TRAINER_OUT = 21, POKEMON_OUT = 15, POKEMON_SPREAD = 1.25;
    private static final double[][] SEATS = {{0}, {-3, 3}, {-5.4, 0, 5.4}, {-7.5, -2.5, 2.5, 7.5}};
    private static final float FACE_STEP = 12;
    private static final int JOIN_EVERY = 40, FAIL_TICKS = 200, CATCH_LIMIT = 20 * 180, SPARKLE_EVERY = 4;
    /** When the Prison Bottle effect starts after the battles have ended, how long after that the copies replace Hoopa, and the longest the battles get to end. */
    private static final int BOTTLE_AT = 20, FORM_AT = 40, SETTLE_LIMIT = 100;
    /** Owns the hidden Hoopa as a prize, so nobody sees it and balls pass through it to the copies on its spot. */
    private static final UUID NOBODY = new UUID(0, 0);
    private static final String MSD_EFFECT = "com.github.yajatkaul.mega_showdown.api.codec.Effect", BOTTLE_EFFECT = "mega_showdown:prison_bottle";
    private static final int FLASH = 8, CLOSE = 12, OPEN = 12;
    /** Ticks into a catch clip the model's rings come back, which is when the thrown ones land. */
    private static final int CATCH_POP = 4;

    /** A ring call: Hoopa's clips, when each ring leaves, the wrist it leaves and the one it lands on, and who comes out. */
    private record Call(String summon, String hold, String grab, int length, int[] at, HoopaSummon.Wrist[] off, HoopaSummon.Wrist[] on, String[] legends) {
    }

    private static final Call[] CALLS = {
            new Call("summon2", "hold2", "catch2", 80, new int[]{44, 52},
                    new HoopaSummon.Wrist[]{new HoopaSummon.Wrist(new double[]{-21.20, 75.43, 45.22}, new double[]{0.093, -0.006, 0.996}, new float[]{0.0039f, 0.0464f, 0.0175f, 0.9988f}),
                            new HoopaSummon.Wrist(new double[]{21.01, 74.33, 46.40}, new double[]{-0.094, -0.034, 0.995}, new float[]{0.0178f, -0.0466f, -0.0162f, 0.9986f})},
                    new HoopaSummon.Wrist[]{new HoopaSummon.Wrist(new double[]{-31.80, 83.07, 24.46}, new double[]{-0.290, 0.439, 0.851}, new float[]{-0.2221f, -0.1591f, -0.0368f, 0.9612f}),
                            new HoopaSummon.Wrist(new double[]{31.80, 83.07, 24.46}, new double[]{0.290, 0.439, 0.851}, new float[]{-0.2221f, 0.1591f, 0.0368f, 0.9612f})},
                    new String[]{"volcanion level=70", "zygarde level=70"}),
            new Call("summon3", "hold3", "catch3", 90, new int[]{44, 52, 60},
                    new HoopaSummon.Wrist[]{new HoopaSummon.Wrist(new double[]{-41.35, 50.09, 36.19}, new double[]{-0.084, -0.049, 0.995}, new float[]{0.0245f, -0.0424f, 0.0050f, 0.9988f}),
                            new HoopaSummon.Wrist(new double[]{11.90, 80.04, 59.60}, new double[]{-0.052, 0.032, 0.998}, new float[]{-0.0156f, -0.0261f, -0.0095f, 0.9995f}),
                            new HoopaSummon.Wrist(new double[]{41.18, 48.54, 37.18}, new double[]{0.083, -0.103, 0.991}, new float[]{0.0519f, 0.0416f, 0.0000f, 0.9978f})},
                    new HoopaSummon.Wrist[]{new HoopaSummon.Wrist(new double[]{-44.29, 57.50, 24.29}, new double[]{-0.403, 0.300, 0.864}, new float[]{-0.1482f, -0.2141f, -0.0329f, 0.9650f}),
                            new HoopaSummon.Wrist(new double[]{31.80, 83.07, 24.46}, new double[]{0.290, 0.439, 0.851}, new float[]{-0.2221f, 0.1591f, 0.0368f, 0.9612f}),
                            new HoopaSummon.Wrist(new double[]{44.29, 57.50, 24.29}, new double[]{0.403, 0.300, 0.864}, new float[]{-0.1482f, 0.2141f, 0.0329f, 0.9650f})},
                    new String[]{"dialga level=90", "giratina orb_forme=origin level=90", "palkia level=90"})};

    private enum Stage { ARRIVE, DESCEND, WAITING, FIGHT, CALL, WAVE, CATCH, OPEN }

    /** Plays the glitch for this many ticks, then the task list stays hidden; 0 brings it back (HoopaGlitch). */
    public record Glitch(int ticks) implements CustomPacketPayload {
        public static final Type<Glitch> TYPE = new Type<>(Hyperspace.id("hoopa_glitch"));
        public static final StreamCodec<ByteBuf, Glitch> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Glitch::ticks, Glitch::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class Event {
        final int island;
        final ServerLevel level;
        final UUID trigger;
        final Vec3 ground, toward;
        final float yaw;
        Stage stage = Stage.ARRIVE;
        int ticks = -1;
        @Nullable PokemonEntity hoopa;
        @Nullable Display.ItemDisplay ring;
        Vec3 ringAt;
        boolean returning, boostDue, hidden, beaten, unseen;
        float landYaw;
        long joinAt = -1, callAt = -1, holdAt = -1, landAt = -1, catchAt = -1, ascendAt = -1;
        Vec3 away;
        int wave = -1, failing;
        @Nullable Call call;
        final List<PokemonEntity> legends = new ArrayList<>();
        final List<HoopaSummon.Summoning> summons = new ArrayList<>();
        @Nullable LegendWave fight;
        final Map<UUID, ItemEntity> bottles = new LinkedHashMap<>();
        final Map<PokemonEntity, UUID> prizes = new LinkedHashMap<>();
        int settled = -1;
        final List<UUID> ready = new ArrayList<>(), seated = new ArrayList<>();

        Event(ServerLevel level, int island, UUID trigger) {
            this.level = level;
            this.island = island;
            this.trigger = trigger;
            BlockPos middle = Zones.middle(island);
            Vec3 centre = new Vec3(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
            Zones.Zone zone = Zones.get(island);
            this.toward = new Vec3(zone.ax, centre.y, zone.az);
            this.ground = centre.subtract(toward.subtract(centre).normalize().scale(HOOPA_BACK));
            this.yaw = HoopaFeed.yawTo(ground, toward);
            this.ringAt = ground;
        }

        long now() {
            return level.getGameTime();
        }
    }

    private static final Map<Integer, Event> events = new HashMap<>();
    private static final Set<UUID> ours = new HashSet<>();
    /** The inside of every summon ring, the most any of Hoopa's summons needs, and the tallest of them, so every ring comes out the same size and height. */
    private static double summonInside, summonHeight;

    private HoopaEvent() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Glitch.TYPE, Glitch.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(HoopaEvent::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG) && !ours.contains(entity.getUUID())) entity.discard();
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer clicker)) return InteractionResult.PASS;
            for (Event e : events.values()) {
                if (e.hoopa != entity) continue;
                if (hand == InteractionHand.MAIN_HAND && e.stage == Stage.WAITING) ready(e, clicker);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Event e : new ArrayList<>(events.values())) clear(e);
            events.clear();
        });
    }

    public static void enter(ServerPlayer player, int island) {
        Event already = events.get(island);
        if (already != null && (already.stage != Stage.ARRIVE || already.ticks >= NORMAL) && already.stage != Stage.OPEN) {
            ServerPlayNetworking.send(player, new Glitch(1));
        }
        if (already != null) return;
        events.put(island, new Event(player.serverLevel(), island, player.getUUID()));
        Hyperspace.LOGGER.info("[hoopa] {} started the Hoopa Unbound event on island {}", player.getName().getString(), island);
    }

    /** True while the event holds the island: return portal small and unusable, and past the opening the timer frozen. */
    public static boolean locked(int island) {
        Event e = events.get(island);
        return e != null && e.stage != Stage.OPEN;
    }

    public static boolean held(int island) {
        return events.containsKey(island);
    }

    public static boolean counting(int island) {
        Event e = events.get(island);
        return e != null && e.stage == Stage.ARRIVE && e.ticks < NORMAL;
    }

    public static @Nullable Component clock(int island, String time) {
        Event e = events.get(island);
        if (e == null || e.stage != Stage.ARRIVE || e.ticks >= NORMAL + GLITCH) return null;
        return scrambled(time, Math.max(0, (e.ticks - NORMAL) * time.length() / GLITCH));
    }

    public static MutableComponent scrambled(String text, int chars) {
        return Component.empty().append(Component.literal(text.substring(0, chars)).withStyle(ChatFormatting.OBFUSCATED))
                .append(Component.literal(text.substring(chars)));
    }

    /** The player is on an island with the event (its catches keep their level: LevelScaling). */
    public static boolean on(ServerPlayer player) {
        WorldData.Trip trip = WorldData.get(player.server).trips.get(player.getUUID());
        return trip != null && events.containsKey(trip.island);
    }

    /** Hoopa Unbound or a legendary it rang in (boss entities carry Boss.TAG before they are added to the world). */
    static boolean ours(Entity entity) {
        return entity.getTags().contains(TAG) || entity.getTags().contains(Boss.TAG) || ours.contains(entity.getUUID());
    }

    public static void ended(int island) {
        Event e = events.remove(island);
        if (e != null) clear(e);
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Event> it = events.values().iterator(); it.hasNext(); ) {
            Event e = it.next();
            try {
                step(e);
            } catch (RuntimeException ex) {
                Hyperspace.LOGGER.error("[hoopa] the event on island {} failed", e.island, ex);
                clear(e);
                it.remove();
            }
        }
    }

    private static void enter(Event e, Stage stage) {
        e.stage = stage;
        e.ticks = 0;
        Hyperspace.LOGGER.info("[hoopa] island {}: {}", e.island, stage);
    }

    private static void step(Event e) {
        e.ticks++;
        bottles(e);
        if (e.hoopa != null && e.hoopa.isRemoved() && e.stage.ordinal() < Stage.OPEN.ordinal()) {
            fail(e, "Hoopa Unbound is gone.");
            return;
        }
        if (e.hoopa != null && e.unseen && e.ticks % UNSEEN_AGAIN == 0) hide(e);
        switch (e.stage) {
            case ARRIVE -> arrive(e);
            case DESCEND -> {
                face(e.hoopa, e.landYaw);
                // not until the clip is playing, or its first frames could show it standing on the ground
                if (e.ticks == REVEAL) e.hoopa.setInvisible(false);
                if (e.ticks == RING_LEAVES) closeRing(e);
                if (e.ticks == DESCEND) landed(e);
            }
            case WAITING -> waiting(e);
            case FIGHT -> fight(e);
            case CALL, WAVE -> wave(e);
            case CATCH -> catching(e);
            case OPEN -> {
            }
        }
    }


    private static void arrive(Event e) {
        if (e.ticks == NORMAL) {
            for (ServerPlayer player : present(e)) ServerPlayNetworking.send(player, new Glitch(GLITCH));
        }
        if (e.ticks == NORMAL + GLITCH) {
            for (ServerPlayer player : present(e)) player.displayClientMessage(Component.empty(), true);
        }
        int t = e.ticks - ARRIVE;
        if (t == 0) {
            for (ServerPlayer player : present(e)) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.8f, 0.5f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1f, 0.6f);
            }
            if (!spawnHoopa(e)) {
                fail(e, null);
                return;
            }
        }
        if (t == RING_AT) openRing(e);
        if (t == RING_AT + 1) HoopaSummon.resize(e.ring, wristScale(e), ringScale(e), GROW, true, HoopaSummon.FLAT);
        if (t == RING_AT + GROW) {
            item(e.ring, ModItems.HOOPA_PORTAL);
            Vec3 c = e.ringAt;
            e.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 120, 4, 0.5, 4, 0.3);
            e.level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.8f);
        }
        if (t == DESCEND_AT) descend(e);
        if (t == DESCEND_AT + 1) enter(e, Stage.DESCEND);
    }

    private static boolean spawnHoopa(Event e) {
        BossDef def = def();
        double perTrainer = def.hpPerTrainer > 0 ? def.hpPerTrainer : BossConfig.get().hpPerTrainer;
        PokemonEntity hoopa = Bosses.spawn(e.level, def, null, e.ground, e.yaw, perTrainer, def.scale > 0 ? def.scale : 1f, new Rules(e));
        if (hoopa == null) return false;
        hoopa.addTag(WormholeDestinations.STAYS_PUT);
        hoopa.addTag(TAG);
        ours.add(hoopa.getUUID());
        e.hoopa = hoopa;
        e.ringAt = e.ground.add(0, RING_UP * HoopaSummon.hoopaScale(hoopa), 0);
        hide(e);
        Boss boss = Bosses.of(hoopa);
        ServerPlayer trigger = e.level.getServer().getPlayerList().getPlayer(e.trigger);
        // hard mode when whoever triggered it has beaten it before
        if (boss != null && trigger != null && trigger.getAttachedOrElse(Wormholes.HOOPA_BEATEN, false)) Bosses.harden(boss);
        return true;
    }

    /** Keeps Hoopa unseen until it's needed. Cobblemon draws a Pokemon whatever its invisible flag says, so this is a ring plane far overhead with Hoopa on the side that doesn't show; HoopaClip forgets one after a minute, so the event's tick sends it again. */
    private static void hide(Event e) {
        e.unseen = true;
        HoopaSummon.sendClip(e.level, e.hoopa, e.ringAt.add(0, UNSEEN_UP, 0), new Vec3(0, 1, 0), true);
    }

    private static float ringScale(Event e) {
        return (float) (RING_INSIDE * HoopaSummon.hoopaScale(e.hoopa) / RING_INNER_UNITS);
    }

    private static void openRing(Event e) {
        Display.ItemDisplay ring = EntityType.ITEM_DISPLAY.create(e.level);
        if (ring == null) return;
        ring.setPos(e.ringAt.x, e.ringAt.y, e.ringAt.z);
        ring.setYRot(e.yaw);
        ((ItemDisplayAccessor) ring).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_PORTAL_CLOSED));
        ((DisplayAccessor) ring).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
        ((DisplayAccessor) ring).hyperspace_wormholes$setViewRange(8f);
        ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(HoopaSummon.flat(wristScale(e)));
        add(e.level, ring);
        e.ring = ring;
        Vec3 c = e.ringAt;
        e.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 1.5, 0.3, 1.5, 0.15);
        e.level.sendParticles(HoopaSummon.dust(0xF3FF00, 2f), c.x, c.y, c.z, 40, 2, 0.3, 2, 0);
        e.level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.7f);
        e.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3f, 0.6f);
    }

    private static void closeRing(Event e) {
        Display.ItemDisplay ring = e.ring;
        if (ring == null) return;
        HoopaSummon.sendClip(e.level, e.hoopa, e.ringAt, new Vec3(0, -1, 0), false);
        item(ring, ModItems.HOOPA_PORTAL_CLOSED);
        HoopaSummon.resize(ring, ringScale(e), wristScale(e), RING_CLOSE, false, HoopaSummon.FLAT);
        e.ring = null;
        Vec3 wrist = wrist(e, e.ground, CAUGHT);
        float yaw = e.hoopa.getYRot();
        float size = thrownSize(e);
        later(e.level, RING_CLOSE, () -> {
            remove(ring);
            HoopaSummon.fly(e.level, e.ringAt, wrist, yaw, CAUGHT, size, RING_HOME, true, null);
        });
        e.level.playSound(null, e.ringAt.x, e.ringAt.y, e.ringAt.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.8f);
    }

    private static void throwRing(Event e) {
        HoopaSummon.fly(e.level, wrist(e, e.hoopa.position(), TOSSED), e.ringAt, e.hoopa.getYRot(), TOSSED, thrownSize(e), RING_THROW, false, () -> {
            if (e.hoopa == null || e.stage == Stage.OPEN) return;
            openRing(e);
            Display.ItemDisplay ring = e.ring;
            HoopaSummon.resize(ring, wristScale(e), ringScale(e), RING_OPEN, true, HoopaSummon.FLAT);
            later(e.level, RING_OPEN, () -> item(ring, ModItems.HOOPA_PORTAL));
        });
    }

    /** The ring as Hoopa wears and throws it (the model's are THROWN_RING_PX wide). */
    private static float thrownSize(Event e) {
        return (float) (THROWN_RING_PX * HoopaSummon.hoopaScale(e.hoopa));
    }

    private static float wristScale(Event e) {
        return HoopaSummon.hoopFor(thrownSize(e));
    }

    private static Vec3 wrist(Event e, Vec3 standing, HoopaSummon.Wrist wrist) {
        double s = HoopaSummon.hoopaScale(e.hoopa);
        Vec3 ahead = Vec3.directionFromRotation(0, e.hoopa.getYRot());
        Vec3 left = new Vec3(ahead.z, 0, -ahead.x);
        double[] at = wrist.at();
        return standing.add(left.scale(at[0] * s)).add(0, at[1] * s, 0).add(ahead.scale(at[2] * s));
    }

    private static void item(@Nullable Display.ItemDisplay display, Item item) {
        if (display != null) ((ItemDisplayAccessor) display).hyperspace_wormholes$setItem(new ItemStack(item));
    }

    /** Hoopa (still hidden) starts its descend clip under the ring, cut at the ring's plane so it comes out of it. */
    private static void descend(Event e) {
        PokemonEntity hoopa = e.hoopa;
        ServerPlayer near = e.returning ? null : nearest(e);
        e.landYaw = near != null ? HoopaFeed.yawTo(e.ground, near.position()) : e.yaw;
        hoopa.moveTo(e.ground.x, e.ground.y, e.ground.z, e.landYaw, 0f);
        face(hoopa, e.landYaw);
        e.unseen = false;
        HoopaSummon.sendClip(e.level, hoopa, e.ringAt, new Vec3(0, -1, 0), true);
        HoopaFeed.send(hoopa, CLIPS + "descend", e.landYaw);
        e.level.playSound(null, e.ringAt.x, e.ringAt.y, e.ringAt.z, SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.6f, 1.6f);
        e.hidden = false;
    }

    private static void landed(Event e) {
        PokemonEntity hoopa = e.hoopa;
        HoopaSummon.sendClip(e.level, hoopa, e.ringAt, new Vec3(0, -1, 0), false);
        hoopa.setNoGravity(false);
        e.level.sendParticles(ParticleTypes.END_ROD, hoopa.getX(), hoopa.getY() + 0.5, hoopa.getZ(), 30, 2, 0.3, 2, 0.08);
        e.level.playSound(null, hoopa.getX(), hoopa.getY(), hoopa.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2f, 0.7f);
        e.failing = 0;
        if (e.returning) {
            Boss boss = Bosses.of(hoopa);
            if (boss != null) boss.nextTurns();
            e.returning = false;
            e.boostDue = true;
            enter(e, Stage.FIGHT);
            return;
        }
        hoopa.cry();
        tell(e, Component.literal("Hoopa is waiting for you. Right-click it when you're ready.").withStyle(ChatFormatting.GOLD));
        enter(e, Stage.WAITING);
    }

    private static void transition(Event e, BossTransitionPayload transition, @Nullable ResourceLocation music) {
        for (ServerPlayer player : present(e)) {
            if (music != null) new BattleMusicPacket(music, 1f, 1f, true).sendToPlayer(player);
            ServerPlayNetworking.send(player, transition);
        }
        e.joinAt = e.now() + transition.black();
    }

    private static void waiting(Event e) {
        List<ServerPlayer> here = present(e);
        ServerPlayer near = nearest(e);
        if (near != null) turn(e.hoopa, near.position(), FACE_STEP);
        List<ServerPlayer> able = here.stream().filter(HoopaEvent::canBattle).toList();
        e.ready.removeIf(id -> able.stream().noneMatch(p -> p.getUUID().equals(id)));
        if (able.isEmpty()) {
            if (++e.failing >= FAIL_TICKS) fail(e, "Nobody left can battle.");
            return;
        }
        e.failing = 0;
        if (!e.ready.isEmpty() && able.stream().allMatch(p -> e.ready.contains(p.getUUID()))) {
            e.seated.clear();
            e.seated.addAll(e.ready);
            transition(e, opening(def()), def().music);
            Hyperspace.LOGGER.info("[hoopa] island {}: opening for {} trainers", e.island, e.seated.size());
            enter(e, Stage.FIGHT);
        }
    }

    /** Marks this player ready for the Hoopa waiting on their island, wherever they stand (used by a command). */
    static boolean ready(ServerPlayer player) {
        WorldData.Trip trip = WorldData.get(player.server).trips.get(player.getUUID());
        Event e = trip == null ? null : events.get(trip.island);
        if (e == null || e.stage != Stage.WAITING) return false;
        ready(e, player);
        return true;
    }

    private static void ready(Event e, ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return;
        if (!canBattle(player)) {
            player.sendSystemMessage(Component.translatable("hyperspace_wormholes.boss.no_pokemon").withStyle(ChatFormatting.RED));
            return;
        }
        if (e.ready.contains(player.getUUID())) return;
        e.ready.add(player.getUUID());
        List<ServerPlayer> able = present(e).stream().filter(HoopaEvent::canBattle).toList();
        if (e.ready.size() >= able.size()) return;
        tell(e, Component.translatable("hyperspace_wormholes.boss.ready", player.getName(), e.ready.size(), able.size())
                .withStyle(ChatFormatting.GOLD));
    }

    private static void arena(Event e, Boss boss, Vec3 facing) {
        List<ServerPlayer> here = present(e);
        face(e.hoopa, e.yaw);
        for (ServerPlayer player : here) if (!e.seated.contains(player.getUUID())) e.seated.add(player.getUUID());
        Vec3 ahead = new Vec3(e.toward.x - e.ground.x, 0, e.toward.z - e.ground.z).normalize();
        Vec3 across = new Vec3(-ahead.z, 0, ahead.x);
        double[] seats = SEATS[Math.clamp(e.seated.size(), 1, SEATS.length) - 1];
        for (ServerPlayer player : here) {
            int index = Math.min(e.seated.indexOf(player.getUUID()), seats.length - 1);
            Vec3 trainer = seat(e, ahead, across, TRAINER_OUT, seats[index]);
            player.teleportTo(e.level, trainer.x, trainer.y, trainer.z, Set.of(), HoopaFeed.yawTo(trainer, facing), 0f);
            boss.reserve(player.getUUID(), seat(e, ahead, across, POKEMON_OUT, seats[index] * POKEMON_SPREAD), trainer);
        }
    }

    private static Vec3 seat(Event e, Vec3 ahead, Vec3 across, double out, double side) {
        Vec3 spot = e.ground.add(ahead.scale(out)).add(across.scale(side));
        Vec3 ground = HoopaSummon.ground(e.level, spot.add(0, 6, 0));
        return ground != null ? ground : spot;
    }

    private static BossTransitionPayload opening(BossDef def) {
        int total = (int) Math.round(def.openingSeconds * 20);
        int hold = Math.max(1, total - FLASH - CLOSE - OPEN);
        return new BossTransitionPayload(false, FLASH, CLOSE, hold, OPEN, 0);
    }


    private static void fight(Event e) {
        Boss boss = Bosses.of(e.hoopa);
        if (boss == null) {
            fail(e, null);
            return;
        }
        long now = e.now();
        if (e.joinAt >= 0 && now >= e.joinAt) {
            e.joinAt = -1;
            arena(e, boss, e.ground);
            joinAll(e, boss);
        } else if (e.joinAt < 0 && e.ticks % JOIN_EVERY == 0) {
            joinAll(e, boss);
        }
        if (boss.phase() == Boss.Phase.CATCH) {
            beaten(e, boss);
            return;
        }
        if (e.boostDue && !boss.battles().isEmpty() && boss.battles().stream().allMatch(b -> b.getTurn() >= 1)) {
            e.boostDue = false;
            boss.powerUp(Map.of("second_lowest", 1));
        }
        Entity aim = boss.aimedAt();
        ServerPlayer near = e.joinAt >= 0 ? nearest(e) : null;
        if (near != null) turn(e.hoopa, near.position(), FACE_STEP);
        else turn(e.hoopa, aim != null ? aim.position() : e.toward, 6);
        if (e.joinAt < 0 && boss.battles().isEmpty() && present(e).stream().noneMatch(HoopaEvent::canBattle)) {
            if (++e.failing >= FAIL_TICKS) fail(e, "Nobody left can battle.");
        } else {
            e.failing = 0;
        }
    }

    private static void joinAll(Event e, Boss boss) {
        for (ServerPlayer player : present(e)) {
            if (boss.fighting(player) || !canBattle(player)) continue;
            boss.join(player, null);
        }
    }

    /** Hit 80% or 40%: called from the boss's tick (Rules.formChange), with its battles paused from here on. */
    private static void call(Event e, int wave) {
        e.wave = wave;
        e.call = CALLS[wave];
        enter(e, Stage.CALL);
        e.ticks = -1;
    }

    private static void wave(Event e) {
        Call call = e.call;
        Boss boss = Bosses.of(e.hoopa);
        if (call == null || boss == null) {
            fail(e, null);
            return;
        }
        long now = e.now();
        if (e.stage == Stage.CALL && e.ticks == 0) ringIn(e, call);
        hoopaAway(e, call, now);
        if (e.stage == Stage.CALL) {
            if (e.hidden && !e.summons.isEmpty() && e.summons.stream().allMatch(HoopaSummon.Summoning::out)) {
                e.fight = new LegendWave(boss, e.legends, Math.max(1, boss.battles().size()));
                enter(e, Stage.WAVE);
            }
            return;
        }
        LegendWave fight = e.fight;
        fight.tick();
        if (fight.cleared()) {
            if (!e.legends.isEmpty() && fight.settled()) {
                fight.finish();
                for (PokemonEntity legend : e.legends) vanish(e, legend);
                e.legends.clear();
            }
            if (e.legends.isEmpty() && e.hidden) comeBack(e);
            return;
        }
        if (e.ticks % JOIN_EVERY == 0) joinAll(e, boss);
        if (boss.battles().isEmpty() && present(e).stream().noneMatch(HoopaEvent::canBattle)) {
            if (++e.failing >= FAIL_TICKS) fail(e, "Nobody left can battle.");
        } else {
            e.failing = 0;
        }
    }

    /** The legendaries come out of rings Hoopa throws, at spots in front of it, left to right as the players see it. */
    private static void ringIn(Event e, Call call) {
        PokemonEntity hoopa = e.hoopa;
        face(hoopa, e.yaw);
        HoopaFeed.send(hoopa, CLIPS + call.summon, e.yaw);
        e.holdAt = e.now() + call.length - HOLD_EARLY;
        e.catchAt = -1;
        e.ascendAt = -1;
        e.summons.clear();
        e.legends.clear();
        Vec3 ahead = new Vec3(e.toward.x - e.ground.x, 0, e.toward.z - e.ground.z).normalize();
        Vec3 viewersLeft = new Vec3(-ahead.z, 0, ahead.x);
        e.callAt = e.now();
        e.away = e.ground.subtract(ahead.scale(MOVE_BACK)).add(0, MOVE_UP, 0);
        hoopa.setNoGravity(true);
        int n = call.legends.length;
        for (int i = 0; i < n; i++) {
            PokemonEntity legend;
            try {
                legend = PokemonProperties.Companion.parse(call.legends[i] + " uncatchable").createEntity(e.level, null);
            } catch (RuntimeException ex) {
                Hyperspace.LOGGER.error("[hoopa] couldn't make {}", call.legends[i], ex);
                continue;
            }
            legend.setInvulnerable(true);
            legend.setPersistenceRequired();
            legend.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
            legend.addTag(TAG);
            legend.addTag(WormholeDestinations.STAYS_PUT);
            legend.setDrops(new DropTable());
            ours.add(legend.getUUID());
            double across = ((n - 1) / 2.0 - i) * (HoopaSummon.outsideFor(summonInside(e)) + RING_GAP);
            Vec3 spot = e.ground.add(ahead.scale(LEGEND_FRONT)).add(viewersLeft.scale(across));
            Vec3 ground = HoopaSummon.ground(e.level, spot.add(0, 4, 0));
            if (ground == null) ground = spot;
            e.legends.add(legend);
            e.summons.add(HoopaSummon.ringIn(hoopa, legend, ground, ground.add(ahead), call.at[i], call.off[i], call.on[i], THROWN_RING_PX, e.away,
                    e.ground.y + HoopaSummon.aboveFor(summonHeight), summonInside(e)));
        }
        int land = 0;
        for (HoopaSummon.Summoning summon : e.summons) land = Math.max(land, summon.landing());
        for (HoopaSummon.Summoning summon : e.summons) summon.landAt(land);
        e.landAt = Math.max(e.holdAt + 1, e.callAt + 1 + land);
    }

    private static double summonInside(Event e) {
        if (summonInside > 0) return summonInside;
        for (Call call : CALLS) {
            for (String legend : call.legends) {
                PokemonEntity sized = PokemonProperties.Companion.parse(legend).createEntity(e.level, null);
                summonInside = Math.max(summonInside, HoopaSummon.insideFor(sized));
                summonHeight = Math.max(summonHeight, sized.getBbHeight());
            }
        }
        return summonInside;
    }

    private static void hoopaAway(Event e, Call call, long now) {
        PokemonEntity hoopa = e.hoopa;
        if (!e.hidden) face(hoopa, e.yaw);
        if (!e.hidden && e.ascendAt < 0 && now <= e.callAt + MOVE_TICKS) {
            glide(hoopa, e.ground, e.away, (now - e.callAt) / (float) MOVE_TICKS);
        } else if (!e.hidden && e.ascendAt >= 0) {
            // back under the sky ring as it goes up, or it would fly past the ring's edge
            glide(hoopa, e.away, new Vec3(e.ground.x, e.away.y, e.ground.z), (now - e.ascendAt) / (float) MOVE_TICKS);
        }
        if (now == e.holdAt) HoopaFeed.send(hoopa, CLIPS + call.hold, e.yaw);
        if (e.catchAt < 0 && now > e.holdAt && now >= e.landAt - CATCH_POP) {
            e.catchAt = now;
            HoopaFeed.send(hoopa, CLIPS + call.grab, e.yaw);
        }
        if (e.catchAt >= 0 && e.ascendAt < 0 && now >= e.catchAt + CATCH) {
            e.ascendAt = now;
            HoopaFeed.send(hoopa, CLIPS + "ascend", e.yaw);
            e.level.playSound(null, hoopa.getX(), hoopa.getY(), hoopa.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 2f, 0.5f);
        }
        // not before the clip dips under the ring: raised for the wave, its hair starts out above it
        if (e.ascendAt >= 0 && now == e.ascendAt + ASCEND_CUT) HoopaSummon.sendClip(e.level, hoopa, e.ringAt, new Vec3(0, -1, 0), true);
        if (e.ascendAt >= 0 && now == e.ascendAt + RING_TOSS) throwRing(e);
        if (e.ascendAt >= 0 && !e.hidden && now >= e.ascendAt + ASCEND_HIDE) {
            hoopa.setInvisible(true);
            hide(e);
            e.hidden = true;
        }
    }

    private static void glide(PokemonEntity hoopa, Vec3 from, Vec3 to, float t) {
        float eased = Mth.clamp(t, 0f, 1f);
        eased = eased * eased * (3 - 2 * eased);
        hoopa.setPos(from.lerp(to, eased));
        hoopa.setDeltaMovement(Vec3.ZERO);
    }

    private static void comeBack(Event e) {
        e.returning = true;
        descend(e);
        e.fight = null;
        e.summons.clear();
        enter(e, Stage.DESCEND);
    }

    private static void vanish(Event e, PokemonEntity legend) {
        if (legend.isRemoved()) return;
        double h = legend.getBbHeight() / 2;
        e.level.sendParticles(ParticleTypes.REVERSE_PORTAL, legend.getX(), legend.getY() + h, legend.getZ(), 60, 0.6, h, 0.6, 0.2);
        e.level.sendParticles(HoopaSummon.dust(0xF3FF00, 1.4f), legend.getX(), legend.getY() + h, legend.getZ(), 20, 0.6, h, 0.6, 0);
        e.level.playSound(null, legend.getX(), legend.getY(), legend.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.3f, 1.8f);
        ours.remove(legend.getUUID());
        legend.discard();
    }


    private static void beaten(Event e, Boss boss) {
        e.beaten = true;
        List<ServerPlayer> here = present(e);
        for (ServerPlayer player : here) {
            player.setAttached(Wormholes.HOOPA_BEATEN, true);
            HyperspaceAdvancements.hoopa(player);
        }
        for (ServerPlayer player : boss.players()) player.setAttached(Wormholes.HOOPA_BEATEN, true);
        placeBottles(e, here);
        enter(e, Stage.CATCH);
    }

    private static void catching(Event e) {
        PokemonEntity hoopa = e.hoopa;
        if (e.settled < 0) {
            Boss boss = Bosses.of(hoopa);
            if (boss != null && !boss.battles().isEmpty() && e.ticks < SETTLE_LIMIT) return;
            e.settled = e.ticks;
            recall(e);
            if (hoopa != null) Bosses.release(hoopa);
        }
        int since = e.ticks - e.settled;
        if (hoopa != null && since == BOTTLE_AT) bottleEffect(hoopa);
        if (hoopa != null && since == BOTTLE_AT + FORM_AT) prizes(e, hoopa);
        if (since <= BOTTLE_AT + FORM_AT) return;
        for (Iterator<Map.Entry<PokemonEntity, UUID>> it = e.prizes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PokemonEntity, UUID> prize = it.next();
            PokemonEntity copy = prize.getKey();
            ServerPlayer owner = e.level.getServer().getPlayerList().getPlayer(prize.getValue());
            if (!copy.isRemoved() && (owner == null || owner.level() != e.level)) copy.discard();
            if (copy.isRemoved()) {
                NecrozmaFight.forgetPrize(copy);
                it.remove();
            } else {
                turn(copy, owner.position(), FACE_STEP);
            }
        }
        if (e.prizes.isEmpty() || e.ticks >= CATCH_LIMIT) open(e);
    }

    private static void recall(Event e) {
        for (ServerPlayer player : present(e)) {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                PokemonEntity out = pokemon.getEntity();
                if (out != null && !out.isRemoved()) out.recallWithAnimation();
            }
        }
    }

    /** The Prison Bottle's own effect, by reflection so Mega Showdown isn't needed to build; prizes sets the form if this can't. */
    private static void bottleEffect(PokemonEntity hoopa) {
        try {
            Class<?> type = Class.forName(MSD_EFFECT);
            Object found = type.getMethod("getEffect", String.class).invoke(null, BOTTLE_EFFECT);
            if (found == null) throw new IllegalStateException("no effect " + BOTTLE_EFFECT);
            type.getMethod("revertEffects", Pokemon.class, List.class, Optional.class, PokemonEntity.class)
                    .invoke(found, hoopa.getPokemon(), List.of("djinn_state=confined"), Optional.empty(), null);
            Hyperspace.LOGGER.info("[hoopa] Prison Bottle effect, on its entity {}: {}", hoopa.getPokemon().getEntity() == hoopa, hoopa.getPokemon().getAspects());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            Hyperspace.LOGGER.warn("[hoopa] couldn't play the Prison Bottle effect", ex);
        }
    }

    private static void prizes(Event e, PokemonEntity hoopa) {
        BossDef def = BossDefs.get(DEF);
        String properties = (def != null && def.defeatedProperties != null ? def.defeatedProperties : "djinn_state=confined") + " uncatchable=false";
        Vec3 at = HoopaSummon.ground(e.level, hoopa.position().add(0, 4, 0));
        if (at == null) at = hoopa.position();
        for (ServerPlayer player : present(e)) {
            try {
                Pokemon pokemon = hoopa.getPokemon().clone(true, e.level.registryAccess());
                pokemon.getPersistentData().remove(Boss.DATA);
                pokemon.heal();
                PokemonEntity copy = new PokemonEntity(e.level, pokemon, CobblemonEntities.POKEMON);
                copy.moveTo(at.x, at.y, at.z, hoopa.getYRot(), 0f);
                copy.setNoAi(true);
                copy.setInvulnerable(true);
                copy.addTag(TAG);
                copy.addTag(WormholeDestinations.STAYS_PUT);
                copy.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
                PokemonProperties.Companion.parse(properties).apply(copy);
                ours.add(copy.getUUID());
                NecrozmaFight.prize(copy, player.getUUID());
                e.prizes.put(copy, player.getUUID());
                e.level.addFreshEntity(copy);
                Hyperspace.LOGGER.info("[hoopa] little Hoopa for {}: {}", player.getName().getString(), copy.getUUID());
            } catch (RuntimeException ex) {
                Hyperspace.LOGGER.error("[hoopa] couldn't make the Hoopa of {}", player.getName().getString(), ex);
            }
        }
        hoopa.setInvisible(true);
        hide(e);
        NecrozmaFight.prize(hoopa, NOBODY);
    }

    private static void forgetPrizes(Event e) {
        if (e.hoopa != null) NecrozmaFight.forgetPrize(e.hoopa);
        for (PokemonEntity copy : e.prizes.keySet()) {
            NecrozmaFight.forgetPrize(copy);
            remove(copy);
        }
        e.prizes.clear();
    }

    private static void fail(Event e, @Nullable String why) {
        if (e.stage == Stage.OPEN) return;
        Hyperspace.LOGGER.info("[hoopa] island {}: the event fails ({})", e.island, why);
        open(e);
    }

    private static void open(Event e) {
        float full = e.hoopa != null ? ringScale(e) : 1, hoop = e.hoopa != null ? wristScale(e) : 1;
        if (e.fight != null) e.fight.close();
        e.fight = null;
        for (PokemonEntity legend : e.legends) vanish(e, legend);
        e.legends.clear();
        forgetPrizes(e);
        if (e.hoopa != null) {
            Bosses.release(e.hoopa);
            if (!e.hoopa.isRemoved()) vanish(e, e.hoopa);
            e.hoopa = null;
        }
        if (e.ring != null) {
            Display.ItemDisplay ring = e.ring;
            item(ring, ModItems.HOOPA_PORTAL_CLOSED);
            HoopaSummon.resize(ring, full, hoop, RING_CLOSE, false, HoopaSummon.FLAT);
            e.ring = null;
            later(e.level, RING_CLOSE, () -> remove(ring));
        }
        for (ServerPlayer player : present(e)) ServerPlayNetworking.send(player, new Glitch(0));
        enter(e, Stage.OPEN);
    }


    /** One sparkling Prison Bottle on the floor for each player here who never had one. */
    private static void placeBottles(Event e, List<ServerPlayer> players) {
        Item bottle = BuiltInRegistries.ITEM.get(BOTTLE);
        if (bottle == null || bottle == Items.AIR) {
            Hyperspace.LOGGER.warn("[hoopa] no {} item, so no Prison Bottles", BOTTLE);
            return;
        }
        Vec3 ahead = new Vec3(e.toward.x - e.ground.x, 0, e.toward.z - e.ground.z).normalize();
        Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
        int i = 0;
        for (ServerPlayer player : players) {
            if (player.getAttachedOrElse(Wormholes.PRISON_BOTTLE_TAKEN, false)) continue;
            Vec3 spot = e.ground.add(ahead.scale(5)).add(side.scale((i++ - (players.size() - 1) / 2.0) * 1.5));
            Vec3 ground = HoopaSummon.ground(e.level, spot.add(0, 4, 0));
            if (ground == null) ground = spot;
            ItemEntity item = new ItemEntity(e.level, ground.x, ground.y + 0.1, ground.z, new ItemStack(bottle));
            item.setDeltaMovement(Vec3.ZERO);
            item.setTarget(player.getUUID());
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
            e.level.addFreshEntity(item);
            e.bottles.put(player.getUUID(), item);
            Hyperspace.LOGGER.info("[hoopa] a Prison Bottle for {}", player.getName().getString());
        }
    }

    private static void bottles(Event e) {
        if (e.bottles.isEmpty()) return;
        Item bottle = BuiltInRegistries.ITEM.get(BOTTLE);
        for (Iterator<Map.Entry<UUID, ItemEntity>> it = e.bottles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ItemEntity> entry = it.next();
            ItemEntity item = entry.getValue();
            if (item.isRemoved()) {
                ServerPlayer player = e.level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null && player.getInventory().hasAnyMatching(stack -> stack.is(bottle))) {
                    player.setAttached(Wormholes.PRISON_BOTTLE_TAKEN, true);
                    Hyperspace.LOGGER.info("[hoopa] {} took their Prison Bottle", player.getName().getString());
                }
                it.remove();
                continue;
            }
            if (e.ticks % SPARKLE_EVERY == 0) {
                e.level.sendParticles(e.ticks % (SPARKLE_EVERY * 2) == 0 ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD,
                        item.getX(), item.getY() + 0.4, item.getZ(), 2, 0.2, 0.25, 0.2, 0.01);
            }
        }
    }


    private static BossDef def() {
        BossDef def = BossDefs.get(DEF);
        if (def == null) {
            Hyperspace.LOGGER.error("[hoopa] no boss data {}; Hoopa fights with the defaults", DEF);
            def = BossDef.empty(DEF);
        }
        return def;
    }

    private static List<ServerPlayer> present(Event e) {
        Map<UUID, WorldData.Trip> trips = WorldData.get(e.level.getServer()).trips;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : e.level.players()) {
            WorldData.Trip trip = trips.get(player.getUUID());
            if (trip != null && trip.island == e.island && player.isAlive() && !player.isSpectator()) out.add(player);
        }
        return out;
    }

    private static @Nullable ServerPlayer nearest(Event e) {
        ServerPlayer near = null;
        Vec3 from = e.hoopa != null ? e.hoopa.position() : e.ground;
        for (ServerPlayer player : present(e)) if (near == null || player.position().distanceToSqr(from) < near.position().distanceToSqr(from)) near = player;
        return near;
    }

    private static boolean canBattle(ServerPlayer player) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return false;
        for (Pokemon mon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (!mon.isFainted()) return true;
        return false;
    }

    private static void tell(Event e, Component line) {
        for (ServerPlayer player : present(e)) player.sendSystemMessage(line);
    }

    private static void face(PokemonEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yRotO = yaw;
        entity.yBodyRotO = yaw;
        entity.yHeadRotO = yaw;
    }

    private static void turn(PokemonEntity entity, Vec3 at, float step) {
        float want = HoopaFeed.yawTo(entity.position(), at);
        face(entity, Mth.approachDegrees(entity.getYRot(), want, step));
    }

    private static void add(ServerLevel level, Display display) {
        display.addTag(TAG);
        ours.add(display.getUUID());
        level.addFreshEntity(display);
    }

    private static void remove(@Nullable Entity entity) {
        if (entity == null) return;
        ours.remove(entity.getUUID());
        entity.discard();
    }

    private static void later(ServerLevel level, int ticks, Runnable run) {
        long at = level.getGameTime() + ticks;
        LATER.add(new Later(level, at, run));
    }

    private record Later(ServerLevel level, long at, Runnable run) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> LATER.removeIf(l -> {
            if (l.level.getGameTime() < l.at) return false;
            l.run.run();
            return true;
        }));
    }

    private static void clear(Event e) {
        if (e.fight != null) e.fight.close();
        for (PokemonEntity legend : e.legends) remove(legend);
        forgetPrizes(e);
        if (e.hoopa != null) {
            Bosses.release(e.hoopa);
            remove(e.hoopa);
        }
        remove(e.ring);
        for (ItemEntity item : e.bottles.values()) item.discard();
        e.bottles.clear();
    }

    private static final class Rules implements Boss.Rules {
        private final Event e;

        Rules(Event e) {
            this.e = e;
        }

        @Override
        public boolean defeated(Boss boss) {
            for (ServerPlayer player : boss.players()) player.setAttached(Wormholes.HOOPA_BEATEN, true);
            boss.endAllAsWins();
            return true;
        }

        @Override
        public boolean saved() {
            return false;
        }

        @Override
        public float fleeDistance(float usual) {
            return -1;
        }

        @Override
        public boolean formChange(Boss boss, BossDef.Form form) {
            int wave = def().forms.indexOf(form);
            if (e.stage != Stage.FIGHT || wave < 0 || wave >= CALLS.length) return false;
            call(e, wave);
            return true;
        }

        @Override
        public boolean passesForms() {
            return true;
        }

        @Override
        public boolean lopsided() {
            return true;
        }

        @Override
        public boolean startsHidden() {
            return true;
        }
    }
}
