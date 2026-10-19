package dev.hyperspacewormholes.hyperspace;

import com.bedrockk.molang.runtime.MoLangRuntime;
import com.bedrockk.molang.runtime.struct.QueryStruct;
import com.bedrockk.molang.runtime.value.DoubleValue;
import com.cobblemon.mod.common.api.molang.ExpressionLike;
import com.cobblemon.mod.common.api.molang.MoLangFunctions;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline;
import com.cobblemon.mod.common.api.moves.animations.ActionEffects;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.AnimationActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityConditionalActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.PauseActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.RemoveHoldsActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the quick battles in Hyperspace. Your sent-out Pokemon runs over to a floating ball or wild Pokemon
 * and uses the move you picked, then this handles reach, cooldowns, PP and the damage or pop. The packets for it live
 * here too. Big Pokemon only path where they actually fit (hyperspace_wormholes_fit_path tag) so they don't get stuck
 * in gaps.
 */
public class QuickBattles {
    public static final String FIT_PATH = "hyperspace_wormholes_fit_path";
    private static final double RANGE = 48;
    private static final double TARGET_RANGE = 10;
    private static final double REACH = 2.5;
    private static final int PHYSICAL_CONTACT = 8;
    private static final int SPECIAL_CONTACT = 12;
    private static final int MAX_CONTACT = 100;
    private static final int REPATH = 10;
    private static final float SPEED = 0.35f;
    private static final float SPRINT_SPEED = 0.6f;
    private static final double SPRINT_FROM = 8;
    private static final double SPRINT_UNTIL = 4;
    private static final float SEARCH = 4f;
    private static final double STUCK_MOVE = 0.25;
    private static final int STUCK_REPATHS = 2;
    private static final int GROUND_DROP = 6;
    private static final int LEVEL_GAP = 20;
    private static final int CLOSE_GAP = 5;
    private static final Set<String> TWO_TO_FIVE = Set.of("armthrust", "barrage", "bonerush", "bulletseed", "cometpunch", "doubleslap", "furyattack",
            "furyswipes", "iciclespear", "pinmissile", "rockblast", "scaleshot", "spikecannon", "tailslap", "watershuriken");
    private static final Set<String> TWICE = Set.of("bonemerang", "doublehit", "doublekick", "doubleironbash", "dragondarts", "dualchop",
            "dualwingbeat", "geargrind", "tachyoncutter", "twinbeam", "twineedle");
    private static final Set<String> THRICE = Set.of("surgingstrikes", "tripledive");
    private static final Set<String> ESCALATING = Set.of("triplekick", "tripleaxel");
    private static final Set<String> NEEDS_HIT = Set.of("counter", "mirrorcoat", "metalburst", "comeuppance", "shelltrap", "bide");
    private static final List<String> PROTECTED = List.of("legendary", "mythical", "ultra_beast", "paradox");
    private static final int LANDING = 10;
    private static final int GIVE_UP = 200;
    private static final double RECOIL = 0.2;

    private static class Attack {
        final UUID owner;
        final Entity target;
        final Move move;
        final boolean physical;
        final boolean runs;
        final ActionEffectTimeline timeline;
        final int contact;
        boolean ran;
        int age;
        int started = -1;
        Vec3 last;
        int stuck;
        BlockPos goal;
        final Set<BlockPos> avoid = new HashSet<>();

        Attack(UUID owner, Entity target, Move move, boolean physical, int contact, boolean runs) {
            this.owner = owner;
            this.target = target;
            this.move = move;
            this.physical = physical;
            this.runs = runs;
            this.timeline = timeline(move.getName());
            int timed = timeline == null ? -1 : contact(timeline);
            if (timed > 0) contact = timed;
            this.contact = contact > 0 ? Math.min(contact, MAX_CONTACT) : physical ? PHYSICAL_CONTACT : SPECIAL_CONTACT;
        }
    }

    private static final Map<PokemonEntity, Attack> attacks = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private static Set<String> sounds;

    public record Payload(int ball, int slot, int contact, Optional<UUID> pokemon, boolean runs) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "ball_attack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::ball, ByteBufCodecs.VAR_INT, Payload::slot, ByteBufCodecs.VAR_INT, Payload::contact,
                        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Payload::pokemon, ByteBufCodecs.BOOL, Payload::runs, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Aim(Optional<UUID> pokemon) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "ball_aim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Aim> CODEC =
                StreamCodec.composite(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Aim::pokemon, Aim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MoveInfo(String name, int pp, int maxPp, int cooldown) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveInfo> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MoveInfo::name, ByteBufCodecs.VAR_INT, MoveInfo::pp, ByteBufCodecs.VAR_INT, MoveInfo::maxPp,
                ByteBufCodecs.VAR_INT, MoveInfo::cooldown, MoveInfo::new);
    }

    public record Moves(List<MoveInfo> moves, boolean busy) implements CustomPacketPayload {
        public static final Type<Moves> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "ball_moves"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Moves> CODEC =
                StreamCodec.composite(MoveInfo.CODEC.apply(ByteBufCodecs.list()), Moves::moves, ByteBufCodecs.BOOL, Moves::busy, Moves::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(Aim.TYPE, Aim.CODEC);
        PayloadTypeRegistry.playS2C().register(Moves.TYPE, Moves.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> order(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(Aim.TYPE, (payload, context) -> sendMoves(context.player(), payload.pokemon()));
        ServerTickEvents.END_SERVER_TICK.register(QuickBattles::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            attacks.clear();
            cooldowns.clear();
        });
    }

    public static boolean quickUsable(MoveTemplate move) {
        return move.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS() && !NEEDS_HIT.contains(move.getName());
    }

    public static int cooldown(MoveTemplate move) {
        return (int) Math.round(20 * (1 + move.getPower() / 10));
    }

    public static PokemonEntity sentOut(Player player, Optional<UUID> selected) {
        PokemonEntity best = null;
        for (PokemonEntity entity : player.level().getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(RANGE))) {
            if (!entity.isAlive() || entity.isBattling() || entity.isVehicle() || !player.getUUID().equals(entity.getOwnerUUID())) continue;
            if (selected.isPresent() && selected.get().equals(entity.getPokemon().getUuid())) return entity;
            if (best == null || entity.distanceToSqr(player) < best.distanceToSqr(player)) best = entity;
        }
        return best;
    }

    private static void sendMoves(ServerPlayer player, Optional<UUID> selected) {
        PokemonEntity pokemon = sentOut(player, selected);
        List<MoveInfo> moves = pokemon == null ? List.of() : pokemon.getPokemon().getMoveSet().getMoves().stream()
                .map(move -> new MoveInfo(move.getName(), move.getCurrentPp(), move.getMaxPp(), cooldown(player.server, pokemon, move)))
                .toList();
        ServerPlayNetworking.send(player, new Moves(moves, pokemon != null && attacks.containsKey(pokemon)));
    }

    private static void order(ServerPlayer player, Payload order) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return;
        Entity target = player.level().getEntity(order.ball());
        if (target == null || !target.isAlive() || target.distanceTo(player) > TARGET_RANGE) return;
        PokemonEntity pokemon = sentOut(player, order.pokemon());
        if (pokemon == null || attacks.containsKey(pokemon)) return;
        if (!(target instanceof FloatingBall ball && ball.belongsTo(player) || wild(target))) return;
        List<Move> moves = pokemon.getPokemon().getMoveSet().getMoves();
        if (order.slot() < 0 || order.slot() >= moves.size()) return;
        Move move = moves.get(order.slot());
        if (!quickUsable(move.getTemplate()) || move.getCurrentPp() <= 0 || cooldown(player.server, pokemon, move) > 0) return;
        move.setCurrentPp(move.getCurrentPp() - 1);
        cooldowns.computeIfAbsent(pokemon.getPokemon().getUuid(), u -> new HashMap<>())
                .put(move.getName(), (long) player.server.getTickCount() + cooldown(move.getTemplate()));
        boolean physical = move.getTemplate().getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
        attacks.put(pokemon, new Attack(player.getUUID(), target, move, physical, order.contact(), order.runs()));
        sendMoves(player, order.pokemon());
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<PokemonEntity, Attack>> it = attacks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PokemonEntity, Attack> entry = it.next();
            if (!step(server, entry.getKey(), entry.getValue())) continue;
            it.remove();
            freed(server, entry.getKey(), entry.getValue());
        }
    }

    private static boolean step(MinecraftServer server, PokemonEntity pokemon, Attack attack) {
        Entity target = attack.target;
        if (!pokemon.isAlive() || pokemon.isRemoved() || !target.isAlive() || pokemon.level() != target.level()
                || !(target instanceof FloatingBall || wild(target))) return true;
        attack.age++;
        pokemon.getLookControl().setLookAt(target);
        if (pokemon.isVehicle() || attack.started < 0 && attack.age > GIVE_UP) {
            callOff(pokemon, attack);
            return true;
        }
        if (attack.started < 0) {
            if (!attack.physical || pokemon.getBoundingBox().inflate(REACH).intersects(target.getBoundingBox())) {
                pokemon.setSprinting(false);
                start(server, pokemon, attack);
            } else {
                sprint(pokemon, attack, target);
                if (attack.age % REPATH == 1) chase(pokemon, attack, target);
            }
            return false;
        }
        if (attack.age - attack.started < attack.contact) return false;
        hit(server, pokemon, attack);
        return true;
    }

    private static void callOff(PokemonEntity pokemon, Attack attack) {
        attack.move.setCurrentPp(Math.min(attack.move.getMaxPp(), attack.move.getCurrentPp() + 1));
        Map<String, Long> timers = cooldowns.get(pokemon.getPokemon().getUuid());
        if (timers != null) timers.remove(attack.move.getName());
        pokemon.getNavigation().stop();
        pokemon.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void sprint(PokemonEntity pokemon, Attack attack, Entity target) {
        double distance = pokemon.distanceTo(target);
        boolean sprinting = attack.runs && (pokemon.isSprinting() ? distance > SPRINT_UNTIL : distance > SPRINT_FROM);
        if (sprinting == pokemon.isSprinting()) return;
        pokemon.setSprinting(sprinting);
        pokemon.getNavigation().setSpeedModifier(sprinting ? SPRINT_SPEED : SPEED);
        if (attack.goal != null) pokemon.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(attack.goal, sprinting ? SPRINT_SPEED : SPEED, 0));
    }

    private static void freed(MinecraftServer server, PokemonEntity pokemon, Attack attack) {
        pokemon.getNavigation().resetMaxVisitedNodesMultiplier();
        pokemon.setSprinting(false);
        pokemon.removeTag(FIT_PATH);
        ServerPlayer owner = server.getPlayerList().getPlayer(attack.owner);
        if (owner != null) sendMoves(owner, Optional.of(pokemon.getPokemon().getUuid()));
    }

    private static void chase(PokemonEntity pokemon, Attack attack, Entity target) {
        if (attack.last != null && pokemon.position().distanceToSqr(attack.last) < STUCK_MOVE * STUCK_MOVE) {
            if (++attack.stuck >= STUCK_REPATHS && attack.goal != null) {
                attack.avoid.add(attack.goal);
                attack.stuck = 0;
            }
        } else {
            attack.stuck = 0;
        }
        attack.last = pokemon.position();
        pokemon.getNavigation().setMaxVisitedNodesMultiplier(SEARCH);
        Set<BlockPos> spots = approaches(pokemon, target, attack.avoid);
        pokemon.addTag(FIT_PATH);
        Path path = path(pokemon, spots, target);
        if (path == null) {
            pokemon.removeTag(FIT_PATH);
            path = path(pokemon, spots, target);
        }
        if (path == null) return;
        attack.goal = path.getTarget();
        float speed = pokemon.isSprinting() ? SPRINT_SPEED : SPEED;
        pokemon.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(attack.goal, speed, 0));
        pokemon.getNavigation().moveTo(path, speed);
    }

    private static Path path(PokemonEntity pokemon, Set<BlockPos> spots, Entity target) {
        Path path = spots.isEmpty() ? null : pokemon.getNavigation().createPath(spots, 0);
        return path != null ? path : pokemon.getNavigation().createPath(target.blockPosition(), 1);
    }

    private static Set<BlockPos> approaches(PokemonEntity pokemon, Entity target, Set<BlockPos> avoid) {
        Level level = pokemon.level();
        double reach = (target.getBbWidth() + pokemon.getBbWidth()) / 2 + REACH - 0.3;
        int cells = Mth.ceil(reach);
        BlockPos centre = target.blockPosition();
        Set<BlockPos> spots = new HashSet<>();
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                double x = centre.getX() + dx + 0.5 - target.getX(), z = centre.getZ() + dz + 0.5 - target.getZ();
                if ((dx != 0 || dz != 0) && x * x + z * z > reach * reach) continue;
                BlockPos ground = ground(level, centre.offset(dx, 1, dz));
                if (ground != null && !avoid.contains(ground)) spots.add(ground);
            }
        }
        return spots;
    }

    private static BlockPos ground(Level level, BlockPos from) {
        BlockPos.MutableBlockPos pos = from.mutable();
        for (int i = 0; i <= GROUND_DROP; i++, pos.move(0, -1, 0)) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
            BlockPos below = pos.below();
            BlockState floor = level.getBlockState(below);
            if (floor.getCollisionShape(level, below).isEmpty()) continue;
            if (floor.is(BlockTags.FENCES) || floor.is(BlockTags.WALLS) || floor.is(BlockTags.FENCE_GATES)) return null;
            return pos.immutable();
        }
        return null;
    }

    private static void start(MinecraftServer server, PokemonEntity pokemon, Attack attack) {
        attack.started = attack.age;
        pokemon.getNavigation().resetMaxVisitedNodesMultiplier();
        pokemon.getNavigation().stop();
        pokemon.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        attack.ran = attack.timeline != null && run(attack.timeline, pokemon, attack.target);
        if (!attack.ran) {
            new PlayPosableAnimationPacket(pokemon.getId(), new LinkedHashSet<>(animations(attack.move.getName(), attack.physical)), List.of())
                    .sendToPlayers(PlayerLookup.tracking(pokemon));
            String name = attack.move.getName().toLowerCase(Locale.ROOT);
            String actor = has("move." + name + ".actor") ? "move." + name + ".actor" : has("move." + name + ".actor_1") ? "move." + name + ".actor_1" : null;
            if (actor != null) play((ServerLevel) pokemon.level(), pokemon.position(), actor);
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(attack.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.empty().append(pokemon.getDisplayName()).append(" used ")
                    .append(attack.move.getDisplayName()).append("!"), true);
        }
    }

    private static void hit(MinecraftServer server, PokemonEntity pokemon, Attack attack) {
        ServerLevel level = (ServerLevel) pokemon.level();
        Vec3 centre = attack.target.getBoundingBox().getCenter();
        if (!attack.physical && !attack.ran) {
            int hue = attack.move.getType().getHue();
            DustParticleOptions dust = new DustParticleOptions(new Vector3f((hue >> 16 & 255) / 255f, (hue >> 8 & 255) / 255f, (hue & 255) / 255f), 1.2f);
            Vec3 from = pokemon.getEyePosition();
            int steps = (int) Math.ceil(from.distanceTo(centre) * 3);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.lerp(centre, i / (double) steps);
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        String name = attack.move.getName().toLowerCase(Locale.ROOT);
        String sound = has("move." + name + ".target") ? "move." + name + ".target"
                : "impact." + attack.move.getType().getName().toLowerCase(Locale.ROOT);
        if (!attack.ran || !(attack.target instanceof PokemonEntity)) play(level, centre, sound);
        if (attack.target instanceof FloatingBall ball) {
            ServerPlayer owner = server.getPlayerList().getPlayer(attack.owner);
            if (owner != null) {
                Tasks.progress(owner, Tasks.Kind.BALLS);
                if (!ball.isRemoved()) Money.give(owner, ball.money());
            }
            ball.pop(level);
        } else if (attack.target instanceof PokemonEntity wild) {
            clash(server, pokemon, wild, attack);
        }
    }

    public static boolean wild(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemon) || !pokemon.isAlive() || pokemon.getOwnerUUID() != null || pokemon.isBattling()) return false;
        Pokemon wild = pokemon.getPokemon();
        if (wild.isFainted() || wild.getShiny()) return false;
        Set<String> labels = wild.getSpecies().getLabels();
        return PROTECTED.stream().noneMatch(labels::contains);
    }

    private static void clash(MinecraftServer server, PokemonEntity mine, PokemonEntity wild, Attack attack) {
        int gap = wild.getPokemon().getLevel() - mine.getPokemon().getLevel();
        ServerPlayer player = server.getPlayerList().getPlayer(attack.owner);
        if (Math.abs(gap) > LEVEL_GAP) {
            PokemonEntity loser = gap < 0 ? wild : mine;
            loser.getPokemon().setCurrentHealth(0);
            if (loser == wild && player != null) Tasks.quickDefeat(player);
            if (player != null) player.displayClientMessage(Component.empty().append(loser.getDisplayName()).append(" fainted!"), true);
            return;
        }
        Pokemon target = wild.getPokemon();
        Pokemon own = mine.getPokemon();
        int damage = damage(own, target, attack.move.getTemplate(), attack.physical, mine.getRandom().nextDouble());
        target.setCurrentHealth(Math.max(0, target.getCurrentHealth() - damage));
        boolean superEffective = TypeChart.effectiveness(attack.move.getType(), target.getTypes()) > 1;
        boolean spared = target.isFainted() || Math.abs(gap) <= CLOSE_GAP && superEffective;
        if (!spared) own.setCurrentHealth(Math.max(0, own.getCurrentHealth() - Mth.ceil(own.getMaxHealth() * RECOIL)));
        if (player == null) return;
        if (target.isFainted()) Tasks.quickDefeat(player);
        player.displayClientMessage(Component.empty().append(wild.getDisplayName())
                .append(target.isFainted() ? " fainted!" : " took " + damage + " damage!"), true);
    }

    private static double hits(MoveTemplate move, Pokemon attacker) {
        String name = move.getName();
        if (ESCALATING.contains(name)) return 6;
        if (TWO_TO_FIVE.contains(name)) {
            float roll = attacker.getEntity() == null ? 0.5f : attacker.getEntity().getRandom().nextFloat();
            return roll < 0.35f ? 2 : roll < 0.7f ? 3 : roll < 0.85f ? 4 : 5;
        }
        if (TWICE.contains(name)) return 2;
        if (THRICE.contains(name)) return 3;
        if (name.equals("populationbomb")) return 10;
        return 1;
    }

    private static int damage(Pokemon attacker, Pokemon defender, MoveTemplate move, boolean physical, double roll) {
        double attack = attacker.getStat(physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK);
        double defence = Math.max(1, defender.getStat(physical ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE));
        double base = (2.0 * attacker.getLevel() / 5 + 2) * move.getPower() * attack / defence / 50 + 2;
        boolean stab = false;
        for (ElementalType type : attacker.getTypes()) stab |= type == move.getElementalType();
        return Math.max(1, (int) Math.floor(base * (stab ? 1.5 : 1) * (0.85 + roll * 0.15) * hits(move, attacker)));
    }

    private static ActionEffectTimeline timeline(String move) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", move.toLowerCase(Locale.ROOT));
        return ActionEffects.INSTANCE.getActionEffects().get(id);
    }

    private static MoLangRuntime runtime() {
        MoLangRuntime runtime = new MoLangRuntime();
        QueryStruct query = runtime.getEnvironment().query;
        MoLangFunctions.INSTANCE.addStandardFunctions(query);
        query.addFunction("missed", params -> DoubleValue.ZERO);
        query.addFunction("failed", params -> DoubleValue.ZERO);
        return runtime;
    }

    private static int contact(ActionEffectTimeline timeline) {
        MoLangRuntime runtime = runtime();
        double seconds = 0, landed = -1;
        for (ActionEffectKeyframe keyframe : timeline.getTimeline()) {
            if (keyframe instanceof PauseActionEffectKeyframe pause) {
                seconds += pause.getPause().resolveDouble(runtime);
                continue;
            }
            seconds += delay(keyframe, runtime);
            if (keyframe instanceof RemoveHoldsActionEffectKeyframe) {
                landed = seconds;
                break;
            }
            if (landed < 0 && keyframe instanceof EntityConditionalActionEffectKeyframe conditional && conditional.getEntityCondition() != null
                    && conditional.getEntityCondition().toString().replace(" ", "").contains("is_user==false")) {
                landed = seconds;
            }
        }
        return landed < 0 ? -1 : (int) Math.round(landed * 20) + LANDING;
    }

    private static double delay(ActionEffectKeyframe keyframe, MoLangRuntime runtime) {
        try {
            Object delay = keyframe.getClass().getMethod("getDelay").invoke(keyframe);
            return delay instanceof ExpressionLike expression ? Math.max(0, expression.resolveDouble(runtime)) : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    private static boolean run(ActionEffectTimeline timeline, PokemonEntity user, Entity target) {
        try {
            List<Object> providers = new ArrayList<>(List.of(new UsersProvider(user), new TargetsProvider(target)));
            timeline.run(new ActionEffectContext(timeline, new HashSet<>(), providers, runtime(), false, false, new ArrayList<>(), user.level()));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static List<String> animations(String move, boolean physical) {
        String name = move.toLowerCase(Locale.ROOT), kind = physical ? "physical" : "special";
        List<String> names = new ArrayList<>();
        ActionEffectTimeline timeline = timeline(name);
        if (timeline != null) {
            for (ActionEffectKeyframe keyframe : timeline.getTimeline()) {
                if (!(keyframe instanceof AnimationActionEffectKeyframe animation)) continue;
                for (String entry : animation.getAnimation()) {
                    String resolved = entry.equals("q.move.name") ? name : entry.equals("q.move.damage_category") ? kind : entry;
                    if (!resolved.startsWith("q.") && !names.contains(resolved)) names.add(resolved);
                }
                break;
            }
        }
        if (!names.contains(name)) names.add(0, name);
        if (!names.contains(kind)) names.add(kind);
        return names;
    }

    private static void play(ServerLevel level, Vec3 at, String sound) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", sound));
        level.playSound(null, at.x, at.y, at.z, event, SoundSource.NEUTRAL, 1f, 1f);
    }

    private static int cooldown(MinecraftServer server, PokemonEntity pokemon, Move move) {
        Map<String, Long> used = cooldowns.get(pokemon.getPokemon().getUuid());
        Long ready = used == null ? null : used.get(move.getName());
        return ready == null ? 0 : (int) Math.max(0, ready - server.getTickCount());
    }

    private static boolean has(String sound) {
        if (sounds == null) {
            sounds = new HashSet<>();
            FabricLoader.getInstance().getModContainer("cobblemon").flatMap(mod -> mod.findPath("assets/cobblemon/sounds.json"))
                    .ifPresent(file -> {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            sounds.addAll(JsonParser.parseReader(reader).getAsJsonObject().keySet());
                        } catch (Exception ignored) {
                        }
                    });
        }
        return sounds.contains(sound);
    }
}
