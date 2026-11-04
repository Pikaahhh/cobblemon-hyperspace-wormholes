package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.boss.Boss;
import dev.hyperspacewormholes.wormholes.boss.BossDef;
import dev.hyperspacewormholes.wormholes.boss.BossDefs;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import dev.hyperspacewormholes.wormholes.network.BossTransitionPayload;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import dev.hyperspacewormholes.wormholes.network.UltraBurstPayload;
import dev.hyperspacewormholes.wormholes.world.Zone;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The boss fight against fused Necrozma on the Megalo Tower's summit in the Ultra Megalopolis. Right-clicking it starts
 * one boss.Boss for the party after a screen transition, with an Ultra Burst cutscene at half HP and a defeat scene at
 * 1 HP, then a weakened catchable copy per party trainer.
 */
public final class NecrozmaFight {
    public static final String TAG = "hyperspace_wormholes.necrozma_boss";
    private static final String PRIZE_TAG = "hyperspace_wormholes.necrozma_prize";
    private static final int OWNER_GONE_TICKS = 600;
    public static final int ULTRA_BURST_TICKS = 466;
    public static final int ULTRA_FORM_TICKS = 223;
    private static final ResourceLocation DEF = Wormholes.id("necrozma_megalopolis");
    private static final String SPECIES = "species=cobblemon:necrozma";
    private static final int MAX_PARTY = 4;
    private static final double LOOK_REACH = 48, TELL_REACH = 160;
    private static final Vec3 STAIRS = new Vec3(0, 0, -1), ACROSS = new Vec3(1, 0, 0);
    private static final double BOSS_BACK = 5.0, TRAINER_OUT = 7.2, POKEMON_OUT = 2.5, POKEMON_SPREAD = 1.25;
    private static final double CHALLENGER_AHEAD = 1, GROUP_GAP = 1.5, RIM = 9.0, RIM_FROM = 4, RIM_TO = 13, RIM_STEP = 0.125;
    private static final double[][] SEATS = {{0}, {-2, 2}, {-3.6, 0, 3.6}, {-5.1, -1.7, 1.7, 5.1}};
    private static final int FLASH = 8, CLOSE = 12, OPEN = 12, OPENING_HOLD = 30;
    private static final BossTransitionPayload OUT = new BossTransitionPayload(true, 0, 24, 12, 24, 0);
    private static final BossTransitionPayload BACK = new BossTransitionPayload(false, 0, 12, 20, 12, 0);
    private static final int BURST_AT = OUT.black() + OUT.hold(), BACK_AT = BURST_AT + ULTRA_BURST_TICKS, BACK_DONE = BACK_AT + BACK.length();
    private static final float TURN_STEP = 8, AIM_STEP = 15;
    private static final int RETRY_TICKS = 200;
    private static final String MSD_EFFECT = "com.github.yajatkaul.mega_showdown.api.codec.Effect";
    private static final int START_GRACE_TICKS = 200, EMPTY_TICKS = 100;

    private enum Stage { WAITING, OPENING, BATTLE, BURST, DEFEAT, CATCH }

    private static final class Fight {
        final ServerLevel level;
        final Vec3 spot, arrival;
        final boolean shiny;
        final boolean dusk;
        Stage stage = Stage.WAITING;
        int ticks, age, empty;
        long retryAt;
        boolean poweredDown;
        @Nullable PokemonEntity boss;
        @Nullable BossDef.Form form;
        @Nullable UUID challenger;
        BossTransitionPayload opening = opening(BossDef.empty(DEF));
        final List<Entity> hidden = new ArrayList<>();
        final Set<Entity> stilled = new HashSet<>();
        final Map<PokemonEntity, UUID> prizes = new LinkedHashMap<>();
        final Map<UUID, Integer> away = new HashMap<>();
        final Set<UUID> ready = new LinkedHashSet<>();
        final List<UUID> party = new ArrayList<>();

        Fight(ServerLevel level, Vec3 spot, Vec3 arrival, boolean shiny, boolean dusk) {
            this.level = level;
            this.spot = spot;
            this.arrival = arrival;
            this.shiny = shiny;
            this.dusk = dusk;
        }

        String variant() {
            return dusk ? "dusk" : "dawn";
        }
    }

    private static final List<Fight> FIGHTS = new ArrayList<>();
    private static final Map<Entity, UUID> PRIZES = new HashMap<>();
    private static @Nullable Entity spawning;

    private NecrozmaFight() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NecrozmaFight::tick);
        ServerEntityEvents.ENTITY_LOAD.register(NecrozmaFight::onLoad);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIGHTS.clear());
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer clicker)) return InteractionResult.PASS;
            for (Fight f : FIGHTS) {
                if (f.boss != entity) continue;
                if (hand == InteractionHand.MAIN_HAND) ready(f, clicker);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            UUID owner = PRIZES.get(event.getPokemon());
            if (owner != null && !(event.getPokeBall().getOwner() instanceof ServerPlayer thrower && thrower.getUUID().equals(owner))) {
                event.cancel();
                return Unit.INSTANCE;
            }
            if (!event.getPokemon().getTags().contains(TAG) || catchable(event.getPokemon())) return Unit.INSTANCE;
            event.cancel();
            if (event.getPokeBall().getOwner() instanceof ServerPlayer thrower) {
                thrower.sendSystemMessage(Component.translatable("hyperspace_wormholes.necrozma.ball_knocked")
                        .withStyle(ChatFormatting.RED));
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.LOWEST, event -> {
            if (catchable(event.getPokemonEntity())) event.setCaptureResult(new CaptureContext(4, true, false));
            return Unit.INSTANCE;
        });
    }

    static void arrive(ServerLevel level, Zone zone, Vec3 arrival, @Nullable String properties) {
        if (zone.spot() == null) return;
        Vec3 spot = zone.standOn(zone.spot());
        for (Fight f : FIGHTS) {
            if (f.level == level && f.spot.distanceToSqr(spot) < 1) return;
        }
        Fight f = new Fight(level, spot, arrival, properties != null && properties.contains("shiny=true"),
                level.getRandom().nextBoolean());
        f.boss = spawn(f, arrival);
        if (f.boss == null) return;
        FIGHTS.add(f);
        Wormholes.LOGGER.info("[necrozma] {} Necrozma waits at {}", f.variant(), spot);
    }

    public static boolean isBoss(Entity entity) {
        return !entity.level().isClientSide() && entity.getTags().contains(TAG);
    }

    public static boolean holdsUp(Pokemon pokemon) {
        PokemonEntity entity = pokemon.getEntity();
        return entity != null && !entity.isRemoved() && !entity.level().isClientSide() && pokemon.isWild()
                && entity.getTags().contains(TAG);
    }

    /** Whether this player is in a fight party while one of its scenes has them (MegaloKeepOut leaves them be then). */
    public static boolean inScene(ServerPlayer player) {
        for (Fight f : FIGHTS) {
            if ((f.stage == Stage.OPENING || f.stage == Stage.BURST || f.stage == Stage.DEFEAT)
                    && f.party.contains(player.getUUID())) return true;
        }
        return false;
    }

    private static boolean catchable(PokemonEntity entity) {
        return PRIZES.containsKey(entity);
    }

    /** Makes a boss's catchable copy this player's alone: only they see it and only their balls reach it. */
    public static void prize(PokemonEntity copy, UUID owner) {
        PRIZES.put(copy, owner);
    }

    public static void forgetPrize(PokemonEntity copy) {
        PRIZES.remove(copy);
    }

    public static boolean hiddenFrom(Entity entity, @Nullable Entity viewer) {
        if (PRIZES.isEmpty() || !(entity instanceof PokemonEntity)) return false;
        UUID owner = PRIZES.get(entity);
        return owner != null && (viewer == null || !owner.equals(viewer.getUUID()));
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (entity == spawning || !(entity instanceof PokemonEntity)) return;
        if (entity.getTags().contains(PRIZE_TAG) && !PRIZES.containsKey(entity)
                || entity.getTags().contains(TAG) && FIGHTS.stream().noneMatch(f -> f.boss == entity)) entity.discard();
    }

    private static BossDef def() {
        BossDef def = BossDefs.get(DEF);
        if (def == null) {
            Wormholes.LOGGER.error("No boss data {} for the Megalopolis Necrozma; it fights with Cobblemon's defaults", DEF);
            def = BossDef.empty(DEF);
        }
        return def;
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Fight> it = FIGHTS.iterator(); it.hasNext(); ) {
            Fight f = it.next();
            f.age++;
            boolean anyone = f.level.players().stream().anyMatch(p -> !p.isSpectator());
            f.empty = anyone ? 0 : f.empty + 1;
            if (f.age > START_GRACE_TICKS && f.empty > EMPTY_TICKS) {
                unhide(f);
                clearPrizes(f);
                dropBoss(f);
                it.remove();
                continue;
            }
            if (step(f)) it.remove();
        }
    }

    private static boolean step(Fight f) {
        f.ticks++;
        switch (f.stage) {
            case WAITING -> {
                if (f.boss == null || f.boss.isRemoved()) f.boss = spawn(f, idleLook(f));
                if (f.boss == null) return true;
                turn(f.boss, idleLook(f), TURN_STEP);
                if (f.ready.isEmpty()) return false;
                List<ServerPlayer> present = present(f);
                f.ready.removeIf(id -> present.stream().noneMatch(p -> p.getUUID().equals(id)));
                if (!f.ready.isEmpty() && (f.ready.size() >= MAX_PARTY
                        || present.stream().allMatch(p -> f.ready.contains(p.getUUID())))) start(f);
            }
            case OPENING -> {
                if (f.ticks < f.opening.black()) return false;
                arena(f);
                battle(f);
            }
            case BATTLE -> {
                Boss boss = f.boss == null ? null : Bosses.of(f.boss);
                if (boss != null && !f.boss.isRemoved() && !f.boss.isBusy() && boss.phase() == Boss.Phase.FIGHT) {
                    Entity aim = boss.aimedAt();
                    turn(f.boss, aim != null ? aim.position() : f.boss.position().add(STAIRS), AIM_STEP);
                }
                if (boss != null && !boss.battles().isEmpty()) return false;
                if (boss != null && boss.phase() == Boss.Phase.CATCH) {
                    BossDef def = def();
                    enter(f, Stage.DEFEAT);
                    recallFor(f);
                    cutscene(f, true);
                    sendNear(f, new NecrozmaScenePayload(NecrozmaScenePayload.DEFEAT, f.boss.getId(), def.defeatTicks,
                            ground(f, f.spot), stairsYaw(f)));
                    Wormholes.LOGGER.info("[necrozma] defeat scene for {} ticks, base form at {}", def.defeatTicks, def.defeatFormTicks);
                    return false;
                }
                lost(f);
            }
            case BURST -> burst(f);
            case DEFEAT -> {
                if (f.boss == null || f.boss.isRemoved()) return true;
                BossDef def = def();
                BossDef.DefeatEffect effect = def.defeatEffect;
                if (effect != null && f.ticks == Math.min(effect.at(), def.defeatTicks)) f.poweredDown = powerDown(f, effect);
                if (f.poweredDown) holdStill(f.boss);
                if (f.ticks == Math.min(def.defeatFormTicks, def.defeatTicks)) {
                    Bosses.release(f.boss);
                    weaken(f, !f.poweredDown, def);
                }
                if (f.ticks < def.defeatTicks) return false;
                if (fused(f.boss)) {
                    Wormholes.LOGGER.warn("[necrozma] Necrozma didn't take its own form back by the end of its defeat scene (aspects {}), so it is set",
                            f.boss.getPokemon().getAspects());
                    new StringSpeciesFeature("prism_fusion", "none").apply(f.boss);
                    f.boss.refreshDimensions();
                }
                unhide(f);
                prizes(f);
                enter(f, Stage.CATCH);
                Wormholes.LOGGER.info("[necrozma] catch phase: {} Necrozma, one each for {}", f.prizes.size(),
                        f.prizes.values().stream().map(id -> name(f, id)).toList());
            }
            case CATCH -> {
                for (Iterator<Map.Entry<PokemonEntity, UUID>> it = f.prizes.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<PokemonEntity, UUID> prize = it.next();
                    PokemonEntity copy = prize.getKey();
                    UUID owner = prize.getValue();
                    ServerPlayer player = f.level.getServer().getPlayerList().getPlayer(owner);
                    boolean here = player != null && player.level() == f.level;
                    int gone = here ? 0 : f.away.merge(owner, 1, Integer::sum);
                    if (here) f.away.remove(owner);
                    if (!copy.isRemoved() && gone > OWNER_GONE_TICKS) {
                        copy.discard();
                        Wormholes.LOGGER.info("[necrozma] {} left the summit, so their Necrozma is gone", name(f, owner));
                    } else if (copy.isRemoved()) {
                        Wormholes.LOGGER.info("[necrozma] the Necrozma of {} is caught or gone", name(f, owner));
                    }
                    if (copy.isRemoved()) {
                        it.remove();
                        PRIZES.remove(copy);
                        f.away.remove(owner);
                        continue;
                    }
                    holdStill(copy);
                    faceStairs(f, copy);
                }
                if (f.prizes.isEmpty()) Wormholes.LOGGER.info("[necrozma] every Necrozma is caught or gone; the summit is back to normal");
                return f.prizes.isEmpty();
            }
        }
        return false;
    }

    private static void prizes(Fight f) {
        PokemonEntity real = f.boss;
        if (real == null) return;
        Vec3 at = real.position();
        float yaw = stairsYaw(f);
        for (ServerPlayer player : partyPlayers(f)) {
            try {
                Pokemon pokemon = real.getPokemon().clone(true, f.level.registryAccess());
                pokemon.getPersistentData().remove(Boss.DATA);
                PokemonEntity copy = new PokemonEntity(f.level, pokemon, CobblemonEntities.POKEMON);
                copy.moveTo(at.x, at.y, at.z, yaw, 0f);
                copy.setYHeadRot(yaw);
                copy.setYBodyRot(yaw);
                copy.setNoAi(true);
                copy.setInvulnerable(true);
                copy.addTag(PRIZE_TAG);
                copy.addTag(WormholeDestinations.STAYS_PUT);
                copy.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
                PokemonProperties.Companion.parse("uncatchable=false").apply(copy);
                UltraStays.keep(f.level, copy);
                PRIZES.put(copy, player.getUUID());
                f.prizes.put(copy, player.getUUID());
                spawning = copy;
                try {
                    f.level.addFreshEntity(copy);
                } finally {
                    spawning = null;
                }
            } catch (RuntimeException e) {
                Wormholes.LOGGER.error("[necrozma] couldn't make the Necrozma of {}", player.getName().getString(), e);
            }
        }
        Bosses.release(real);
        real.discard();
        f.boss = null;
    }

    private static float stairsYaw(Fight f) {
        return WildsShadows.facing(f.spot, f.spot.add(STAIRS));
    }

    private static void faceStairs(Fight f, PokemonEntity entity) {
        float yaw = stairsYaw(f);
        if (entity.getYRot() != yaw || entity.yBodyRot != yaw || entity.getYHeadRot() != yaw) face(entity, yaw);
    }

    private static void face(PokemonEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
    }

    private static void clearPrizes(Fight f) {
        for (PokemonEntity copy : f.prizes.keySet()) {
            PRIZES.remove(copy);
            discard(copy);
        }
        f.prizes.clear();
        f.away.clear();
    }

    private static String name(Fight f, UUID id) {
        ServerPlayer player = f.level.getServer().getPlayerList().getPlayer(id);
        return player != null ? player.getName().getString() : id.toString();
    }

    private static void enter(Fight f, Stage stage) {
        f.stage = stage;
        f.ticks = 0;
    }

    private static void lost(Fight f) {
        unhide(f);
        dropBoss(f);
        f.boss = spawn(f, lookAt(f));
        f.retryAt = f.level.getGameTime() + RETRY_TICKS;
        Wormholes.LOGGER.info("[necrozma] the party lost; a fresh fused Necrozma waits");
        f.party.clear();
        enter(f, Stage.WAITING);
    }

    private static void ready(Fight f, ServerPlayer player) {
        if (f.stage != Stage.WAITING || f.boss == null || player.isSpectator() || !player.isAlive()) return;
        if (f.level.getGameTime() < f.retryAt) return;
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return;
        if (!canBattle(player)) {
            player.sendSystemMessage(Component.translatable("hyperspace_wormholes.boss.no_pokemon").withStyle(ChatFormatting.RED));
            return;
        }
        if (!f.ready.contains(player.getUUID()) && f.ready.size() >= MAX_PARTY) return;
        f.ready.add(player.getUUID());
        List<ServerPlayer> present = present(f);
        int needed = Math.min(MAX_PARTY, present.size());
        if (f.ready.size() >= needed) return;
        Component line = Component.translatable("hyperspace_wormholes.boss.ready", player.getName(), f.ready.size(), needed)
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : present) p.sendSystemMessage(line);
    }

    private static boolean canBattle(ServerPlayer player) {
        return PlayerExtensionsKt.party(player).toBattleTeam(false, false, null).stream().anyMatch(p -> p.getHealth() > 0);
    }

    private static List<ServerPlayer> present(Fight f) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : f.level.players()) {
            if (!player.isSpectator() && player.isAlive() && near(f, player)) out.add(player);
        }
        return out;
    }

    private static boolean near(Fight f, ServerPlayer player) {
        return player.distanceToSqr(f.spot) <= TELL_REACH * TELL_REACH;
    }

    private static void start(Fight f) {
        f.party.clear();
        f.party.addAll(f.ready);
        f.ready.clear();
        f.challenger = f.party.getFirst();
        recallFor(f);
        BossDef def = def();
        f.opening = opening(def);
        ResourceLocation music = music(def, f);
        for (ServerPlayer player : partyPlayers(f)) {
            if (music != null) new BattleMusicPacket(music, 1f, 1f, true).sendToPlayer(player);
            ServerPlayNetworking.send(player, f.opening);
        }
        Wormholes.LOGGER.info("[necrozma] opening: {} with {} from its first tick", f.opening, music);
        enter(f, Stage.OPENING);
    }

    private static BossTransitionPayload opening(BossDef def) {
        int total = (int) Math.round(def.openingSeconds * 20);
        int hold = total > 0 ? Math.max(1, total - FLASH - CLOSE - OPEN) : OPENING_HOLD;
        return new BossTransitionPayload(false, FLASH, CLOSE, hold, OPEN, 0);
    }

    private static @Nullable ResourceLocation music(BossDef def, Fight f) {
        BossDef.Variant variant = def.variant(f.variant());
        return variant != null && variant.music() != null ? variant.music() : def.music;
    }

    private static Vec3 bossSpot(Fight f) {
        return f.spot.subtract(STAIRS.scale(BOSS_BACK));
    }

    private static void arena(Fight f) {
        if (f.boss == null || f.party.isEmpty()) return;
        Vec3 boss = ground(f, bossSpot(f));
        Vec3 trainers = f.spot.add(STAIRS.scale(TRAINER_OUT));
        f.boss.setNoGravity(false);
        WildsShadows.place(f.boss, boss, WildsShadows.facing(boss, trainers));
        for (ServerPlayer player : partyPlayers(f)) {
            int index = f.party.indexOf(player.getUUID());
            Vec3 at = trainerSpot(f, index);
            player.teleportTo(f.level, at.x, at.y, at.z, Set.of(), WildsShadows.facing(at, boss), 0f);
            for (PokemonEntity out : sentOut(player)) {
                Vec3 seat = ground(f, pokemonSpot(f, index));
                out.getNavigation().stop();
                WildsShadows.place(out, seat, WildsShadows.facing(seat, boss));
            }
        }
    }

    private static void cutscene(Fight f, boolean centre) {
        if (f.boss == null) return;
        if (centre) {
            f.boss.setNoGravity(false);
            WildsShadows.place(f.boss, ground(f, f.spot), stairsYaw(f));
        }
        List<ServerPlayer> players = partyPlayers(f);
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            double angle = (i - (players.size() - 1) / 2.0) * GROUP_GAP / RIM;
            Vec3 out = STAIRS.scale(Math.cos(angle)).add(ACROSS.scale(Math.sin(angle)));
            Vec3 at = ground(f, f.spot.add(out.scale(rim(f, f.spot, out))));
            player.teleportTo(f.level, at.x, at.y, at.z, Set.of(), WildsShadows.facing(at, f.spot), 0f);
            spots.add(at);
        }
        hide(f);
        Wormholes.LOGGER.info("[necrozma] cutscene layout: Necrozma at {}, {} trainers on the stage rim at {}", f.spot, players.size(), spots);
    }

    private static Vec3 ground(Fight f, Vec3 at) {
        int top = Mth.floor(f.spot.y) + 2;
        for (int y = top; y > top - 12; y--) {
            BlockPos feet = BlockPos.containing(at.x, y, at.z);
            if (!solid(f, feet.below()) || solid(f, feet.above())) continue;
            VoxelShape shape = f.level.getBlockState(feet).getCollisionShape(f.level, feet);
            if (shape.max(Direction.Axis.Y) <= 0.5) return new Vec3(at.x, y + (shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y)), at.z);
        }
        return at;
    }

    private static boolean solid(Fight f, BlockPos pos) {
        return !f.level.getBlockState(pos).getCollisionShape(f.level, pos).isEmpty();
    }

    private static List<PokemonEntity> sentOut(ServerPlayer player) {
        List<PokemonEntity> out = new ArrayList<>();
        PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null) return out;
        for (ActiveBattlePokemon active : battle.getActivePokemon()) {
            BattlePokemon pokemon = active.getBattlePokemon();
            PokemonEntity entity = pokemon == null || !(pokemon.getActor() instanceof PlayerBattleActor) ? null : pokemon.getEntity();
            if (entity != null && !entity.isRemoved()) out.add(entity);
        }
        return out;
    }

    private static void hide(Fight f) {
        for (ServerPlayer player : partyPlayers(f)) {
            for (PokemonEntity out : sentOut(player)) {
                if (out.isInvisible()) continue;
                out.setInvisible(true);
                f.hidden.add(out);
                if (!out.isNoAi()) {
                    out.setNoAi(true);
                    out.getNavigation().stop();
                    out.setDeltaMovement(Vec3.ZERO);
                    f.stilled.add(out);
                }
            }
        }
    }

    private static void unhide(Fight f) {
        for (Entity entity : f.hidden) {
            if (!entity.isRemoved()) entity.setInvisible(false);
        }
        for (Entity entity : f.stilled) {
            if (!entity.isRemoved() && entity instanceof PokemonEntity pokemon) pokemon.setNoAi(false);
        }
        f.hidden.clear();
        f.stilled.clear();
    }

    private static double seat(Fight f, int index) {
        double[] seats = SEATS[Math.min(f.party.size(), SEATS.length) - 1];
        return seats[Math.min(index, seats.length - 1)];
    }

    private static Vec3 trainerSpot(Fight f, int index) {
        return f.spot.add(STAIRS.scale(seatOut(f, index))).add(ACROSS.scale(seat(f, index)));
    }

    private static Vec3 pokemonSpot(Fight f, int index) {
        return f.spot.add(STAIRS.scale(POKEMON_OUT + seatOut(f, index) - TRAINER_OUT)).add(ACROSS.scale(seat(f, index) * POKEMON_SPREAD));
    }

    private static double seatOut(Fight f, int index) {
        double out = trainerOut(f, seat(f, index));
        if (index < 0 || index >= f.party.size() || !f.party.get(index).equals(f.challenger)) return out;
        double back = 0;
        for (int i = 0; i < f.party.size(); i++) {
            if (i != index) back = Math.max(back, trainerOut(f, seat(f, i)));
        }
        return back > 0 ? Math.max(TRAINER_OUT, back - CHALLENGER_AHEAD) : out;
    }

    private static double trainerOut(Fight f, double across) {
        return Math.max(TRAINER_OUT, rim(f, f.spot.add(ACROSS.scale(across)), STAIRS));
    }

    private static double rim(Fight f, Vec3 from, Vec3 dir) {
        double first = -1, last = -1;
        for (double d = RIM_FROM; d <= RIM_TO; d += RIM_STEP) {
            Vec3 at = from.add(dir.scale(d));
            boolean rim = f.level.getBlockState(BlockPos.containing(at.x, f.spot.y - 0.5, at.z)).is(Blocks.POLISHED_ANDESITE);
            if (rim) {
                if (first < 0) first = d;
                last = d;
            } else if (first >= 0) {
                break;
            }
        }
        return first < 0 ? RIM : (first + last) / 2;
    }

    private static List<ServerPlayer> partyPlayers(Fight f) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : f.party) {
            ServerPlayer player = f.level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == f.level && player.isAlive() && !player.isSpectator()) out.add(player);
        }
        return out;
    }

    private static void battle(Fight f) {
        List<ServerPlayer> players = partyPlayers(f);
        if (f.boss == null || f.boss.isRemoved() || players.isEmpty()) {
            f.party.clear();
            enter(f, Stage.WAITING);
            return;
        }
        f.challenger = players.getFirst().getUUID();
        Boss boss = Bosses.adopt(f.boss, def(), f.variant(), new Rules(f));
        // hard mode when the challenger has beaten Necrozma before; whoever triggered it decides
        if (!boss.hard() && players.getFirst().getAttachedOrElse(Wormholes.NECROZMA_BEATEN, false)) Bosses.harden(boss);
        f.boss.setNoGravity(false);
        int joined = 0;
        for (ServerPlayer player : players) {
            int index = f.party.indexOf(player.getUUID());
            boss.reserve(player.getUUID(), pokemonSpot(f, index), trainerSpot(f, index));
            if (boss.join(player, null) instanceof SuccessfulBattleStart) joined++;
        }
        if (joined == 0) {
            Bosses.release(f.boss);
            for (ServerPlayer player : players) new BattleMusicPacket(null, 1f, 1f, true).sendToPlayer(player);
            f.retryAt = f.level.getGameTime() + RETRY_TICKS;
            f.party.clear();
            enter(f, Stage.WAITING);
            return;
        }
        Wormholes.LOGGER.info("[necrozma] fight started: {} ({}) against {} trainers", f.variant(), def().id, joined);
        enter(f, Stage.BATTLE);
    }

    private static void burst(Fight f) {
        Boss boss = f.boss == null ? null : Bosses.of(f.boss);
        if (boss == null || boss.battles().isEmpty() || f.form == null) {
            if (boss != null) boss.resume();
            lost(f);
            return;
        }
        BossDef def = def();
        int t = f.ticks - 1;
        if (t == 0) {
            for (ServerPlayer player : partyPlayers(f)) {
                new BattleMusicPacket(null, 1f, 1f, true).sendToPlayer(player);
                ServerPlayNetworking.send(player, OUT.hidingUi(BACK_AT - OUT.length() + 2));
            }
            Wormholes.LOGGER.info("[necrozma] transformation: music fading, transition out");
        } else if (t == OUT.black()) {
            cutscene(f, true);
        } else if (t == BURST_AT) {
            sendNear(f, new UltraBurstPayload(f.boss.getId(), ULTRA_BURST_TICKS));
            Wormholes.LOGGER.info("[necrozma] Ultra Burst scene for {} ticks", ULTRA_BURST_TICKS);
        } else if (t == BURST_AT + ULTRA_FORM_TICKS) {
            boss.applyForm(f.form);
        } else if (t == BACK_AT) {
            for (ServerPlayer player : partyPlayers(f)) ServerPlayNetworking.send(player, BACK.hidingUi(def.powerUpTicks));
        } else if (t == BACK_AT + BACK.black()) {
            unhide(f);
            arena(f);
        } else if (t == BACK_DONE) {
            ResourceLocation music = f.form.music();
            for (ServerPlayer player : partyPlayers(f)) {
                if (music != null) new BattleMusicPacket(music, 1f, 1f, true).sendToPlayer(player);
            }
            boss.powerUp(f.form.boosts());
            sendNear(f, new NecrozmaScenePayload(NecrozmaScenePayload.POWER_UP, f.boss.getId(), def.powerUpTicks));
            Wormholes.LOGGER.info("[necrozma] Ultra Necrozma's theme {} and power-up scene for {} ticks", music, def.powerUpTicks);
        } else if (t >= BACK_DONE + def.powerUpTicks) {
            boss.resume();
            enter(f, Stage.BATTLE);
            Wormholes.LOGGER.info("[necrozma] the battles carry on");
        }
    }

    private static final class Rules implements Boss.Rules {
        private final Fight f;

        Rules(Fight f) {
            this.f = f;
        }

        @Override
        public boolean defeated(Boss boss) {
            boss.endAllAsWins();
            for (ServerPlayer player : boss.players()) player.setAttached(Wormholes.NECROZMA_BEATEN, true);
            for (ServerPlayer player : boss.players()) dev.hyperspacewormholes.wormholes.WormholesAdvancements.ultraNecrozma(player);
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
            if (f.stage != Stage.BATTLE) return false;
            f.form = form;
            enter(f, Stage.BURST);
            return true;
        }
    }

    private static boolean powerDown(Fight f, BossDef.DefeatEffect effect) {
        if (f.boss == null || effect.id().isEmpty()) return false;
        try {
            Class<?> type = Class.forName(MSD_EFFECT);
            Object found = type.getMethod("getEffect", String.class).invoke(null, effect.id());
            if (found == null) throw new IllegalStateException("no effect " + effect.id());
            type.getMethod("applyEffects", Pokemon.class, List.class, Optional.class, PokemonEntity.class)
                    .invoke(found, f.boss.getPokemon(), effect.aspects(), Optional.empty(), null);
            Wormholes.LOGGER.info("[necrozma] power-down effect {} started ({})", effect.id(), effect.aspects());
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Wormholes.LOGGER.warn("[necrozma] couldn't start the power-down effect {}, so its form is set directly", effect.id(), e);
            return false;
        }
    }

    private static void holdStill(PokemonEntity entity) {
        if (!entity.isNoAi()) entity.setNoAi(true);
    }

    private static boolean fused(PokemonEntity entity) {
        return entity.getPokemon().getAspects().stream().anyMatch(aspect -> aspect.endsWith("-fusion") && !aspect.equals("none-fusion"));
    }

    private static void weaken(Fight f, boolean setForm, BossDef def) {
        if (f.boss == null) return;
        if (setForm) new StringSpeciesFeature("prism_fusion", "none").apply(f.boss);
        String properties = def.defeatedProperties;
        if (!setForm && properties != null) properties = properties.replaceAll("(^|\\s)prism_fusion=\\S*", " ").trim();
        PokemonProperties.Companion.parse((properties != null ? properties : "") + " uncatchable=false").apply(f.boss);
        f.boss.getPokemon().removeHeldItem();
        f.boss.getPokemon().heal();
        f.boss.setBattleId(null);
        f.boss.refreshDimensions();
        if (setForm && fused(f.boss)) Wormholes.LOGGER.warn("Necrozma didn't take its own form back (aspects {})", f.boss.getPokemon().getAspects());
        Wormholes.LOGGER.info("[necrozma] weakened and catchable: {}", f.boss.getPokemon().getAspects());
    }

    private static void recallFor(Fight f) {
        for (ServerPlayer player : partyPlayers(f)) {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                PokemonEntity out = pokemon.getEntity();
                if (out != null && !out.isRemoved()) out.recallWithAnimation();
            }
        }
    }

    private static void sendNear(Fight f, CustomPacketPayload payload) {
        for (ServerPlayer player : f.level.players()) {
            if (near(f, player) && ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
        }
    }

    private static @Nullable PokemonEntity spawn(Fight f, Vec3 lookAt) {
        BossDef def = def();
        BossDef.Variant variant = def.variant(f.variant());
        String properties = SPECIES + " " + def.properties + (variant != null ? " " + variant.properties() : "")
                + " uncatchable shiny=" + f.shiny;
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse(properties).createEntity(f.level);
            if (def.scale > 0) entity.getPokemon().setScaleModifier(def.scale);
            WildsShadows.place(entity, f.spot, WildsShadows.facing(f.spot, lookAt));
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.addTag(TAG);
            entity.addTag(WormholeDestinations.STAYS_PUT);
            UltraStays.keep(f.level, entity);
            entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
            spawning = entity;
            try {
                f.level.addFreshEntity(entity);
            } finally {
                spawning = null;
            }
            return entity;
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't spawn the Megalopolis Necrozma '{}'", properties, e);
            return null;
        }
    }

    private static void discard(@Nullable Entity entity) {
        if (entity != null && !entity.isRemoved()) entity.discard();
    }

    private static void dropBoss(Fight f) {
        if (f.boss != null) Bosses.release(f.boss);
        discard(f.boss);
    }

    private static void turn(PokemonEntity entity, Vec3 at, float step) {
        float want = WildsShadows.facing(entity.position(), at);
        if (Math.abs(Mth.wrapDegrees(want - entity.getYRot())) < 0.01f && Math.abs(Mth.wrapDegrees(want - entity.yBodyRot)) < 0.01f) return;
        face(entity, entity.getYRot() + Mth.clamp(Mth.wrapDegrees(want - entity.getYRot()), -step, step));
    }

    private static void tell(Fight f, Component message) {
        for (ServerPlayer player : f.level.players()) {
            if (near(f, player)) player.sendSystemMessage(message);
        }
    }

    private static @Nullable ServerPlayer challenger(Fight f) {
        if (f.challenger == null) return null;
        for (ServerPlayer player : f.level.players()) {
            if (player.getUUID().equals(f.challenger) && player.isAlive()) return player;
        }
        return null;
    }

    private static Vec3 idleLook(Fight f) {
        ServerPlayer nearest = null;
        double best = LOOK_REACH * LOOK_REACH;
        for (ServerPlayer player : f.level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double dx = player.getX() - f.spot.x, dz = player.getZ() - f.spot.z, d = dx * dx + dz * dz;
            if (d <= best) {
                nearest = player;
                best = d;
            }
        }
        return nearest != null ? nearest.position() : f.arrival;
    }

    private static Vec3 lookAt(Fight f) {
        ServerPlayer player = challenger(f);
        return player != null ? player.position() : idleLook(f);
    }
}
