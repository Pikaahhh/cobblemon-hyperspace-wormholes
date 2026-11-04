package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext;
import com.cobblemon.mod.common.api.battles.model.BattleEndResult;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.network.BossHudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One boss Pokemon: a real wild PokemonEntity (no AI, unhurtable, kept in the world) that up to maxTrainers players fight
 * at once, each in their own Cobblemon battle against a copy of it, played as one shared battle (one boss attack per
 * round, all actions in Showdown's order). Its data is a BossDef and its HP is one pool (ShowdownPool) scaled by the
 * number of trainers. Every message a battle shows is echoed to the other trainers' logs (Bosses.forward) unless an equal
 * line has shown within ECHO_TICKS. The state that has to survive a restart is kept in the Pokemon's persistent data.
 */
public final class Boss {
    public static final String TAG = "hyperspace_wormholes.boss";
    public static final String DATA = "hyperspace_wormholes_boss";
    private static final int STRUCK_TICKS = 40, SETTLE_TICKS = 8, AUTOPLAY_TICKS = 30, ECHO_TICKS = 30;
    private static final double BODY_REACH = 0.95, WATCH_MOVE = 0.5;
    private static final int WATCH_SETTLED = 40;
    private static final double STATUS_SCORE = 0.3;
    private static final Stats[] BATTLE_STATS = {Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    public static boolean autoplay;
    public static @Nullable UUID autoplayExcept;

    public enum Phase { FIGHT, CATCH }

    public interface Rules {
        default BattleAI attacks(BattleAI strong) {
            return strong;
        }

        default void joined(Boss boss, ServerPlayer player, PokemonBattle battle) {}

        default boolean defeated(Boss boss) {
            return false;
        }

        default boolean saved() {
            return true;
        }

        default float fleeDistance(float usual) {
            return usual;
        }

        default boolean formChange(Boss boss, BossDef.Form form) {
            return false;
        }

        /** A big hit goes to the lowest form it reached, skipping those it went past, and past them all if it beats the boss. */
        default boolean passesForms() {
            return false;
        }

        /** Its battles are ready for a LegendWave: doubles with one slot a side instead of singles. */
        default boolean lopsided() {
            return false;
        }

        /** It is invisible from the first, as adding it to the world sends it to the players there and then. */
        default boolean startsHidden() {
            return false;
        }
    }

    record Hud(String name, int level, float hp, Map<String, Integer> stages, String status) {}

    record Seat(Vec3 pokemon, Vec3 trainer) {}

    static final class Line {
        final PokemonBattle from;
        final Component message;
        final String text;
        final int at;
        final Set<UUID> sentTo = new HashSet<>();
        boolean done;

        Line(PokemonBattle from, Component message, int at) {
            this.from = from;
            this.message = message;
            this.text = message.getString();
            this.at = at;
        }
    }

    static final class Trainer {
        final UUID player;
        final String name;
        final Pokemon copy;
        final BossActor actor;
        final PlayerBattleActor side;
        @Nullable PokemonBattle battle;
        @Nullable Hud shown;
        @Nullable Entity watched;
        @Nullable Vec3 watchedAt;
        int shownHp = -1, waitShown, watchedTicks;
        boolean playing;

        Trainer(ServerPlayer player, Pokemon copy, BossActor actor, PlayerBattleActor side) {
            this.player = player.getUUID();
            this.name = player.getName().getString();
            this.copy = copy;
            this.actor = actor;
            this.side = side;
        }

        boolean live() {
            return battle != null && !battle.getEnded();
        }
    }

    final PokemonEntity entity;
    final String id;
    final ServerLevel level;
    final BossDef def;
    final @Nullable String variant;
    final double perTrainer;
    int base;
    final Vec3 home;
    final float yaw;
    final @Nullable Rules rules;
    final List<Trainer> trainers = new ArrayList<>();
    private final Map<UUID, Seat> spots = new HashMap<>();
    private final List<Line> heard = new ArrayList<>();
    Phase phase;
    int hp, max;
    private int shown;
    private int shownForm;
    private int form = -1;
    private final int[] stages = new int[BossDef.STATS.size()];
    private @Nullable UUID struck, last, aiming;
    private int struckAt, phaseTicks, emptyTicks, ticks, idleTicks;
    private boolean pooled, boosted, ultimateUsed, armed, paused;
    private @Nullable LegendWave wave;
    /** Hard mode: every "highest" boost goes to all stats. */
    private boolean hard, ultimateAgainUsed;
    private @Nullable Hud logged;

    Boss(PokemonEntity entity, BossDef def, @Nullable String variant, double perTrainer, Vec3 home, float yaw, double fraction,
         Phase phase, int form, @Nullable Rules rules) {
        this.entity = entity;
        this.id = entity.getUUID().toString();
        this.level = (ServerLevel) entity.level();
        this.def = def;
        this.variant = variant;
        this.perTrainer = perTrainer;
        this.home = home;
        this.yaw = yaw;
        this.phase = phase;
        this.form = Mth.clamp(form, -1, def.forms.size() - 1);
        this.rules = rules;
        this.base = Math.max(1, entity.getPokemon().getStat(Stats.HP));
        this.max = target(1);
        this.hp = Mth.clamp((int) Math.round(fraction * max), 1, max);
        this.shown = hp;
        this.shownForm = this.form;
        Bosses.track(entity.getPokemon(), this);
        mirror();
        save();
    }

    public PokemonEntity entity() {
        return entity;
    }

    public BossDef def() {
        return def;
    }

    public Phase phase() {
        return phase;
    }

    public int hp() {
        return hp;
    }

    public int max() {
        return max;
    }

    public boolean paused() {
        return paused;
    }

    public List<PokemonBattle> battles() {
        List<PokemonBattle> out = new ArrayList<>();
        for (Trainer t : trainers) {
            if (t.live()) out.add(t.battle);
        }
        return out;
    }

    public List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (Trainer t : trainers) {
            ServerPlayer player = t.live() ? online(t) : null;
            if (player != null) out.add(player);
        }
        return out;
    }

    public boolean fighting(ServerPlayer player) {
        Trainer t = trainer(player.getUUID());
        return t != null && t.live();
    }

    public @Nullable Entity currentTarget() {
        if (phase != Phase.FIGHT) return null;
        Trainer t = struck == null ? null : trainer(struck);
        if (t == null || !t.live() || (ticks - struckAt > STRUCK_TICKS && !t.playing)) t = last == null ? null : trainer(last);
        if (t == null || !t.live()) t = trainers.stream().filter(Trainer::live).findFirst().orElse(null);
        return t == null ? null : active(t);
    }

    public @Nullable Entity aimedAt() {
        Trainer t = aiming == null || phase != Phase.FIGHT || paused ? null : trainer(aiming);
        return t == null || !t.live() ? null : active(t);
    }

    private @Nullable UUID aimOf(@Nullable String step) {
        if (step == null || step.contains(",")) return null;
        String[] parts = step.split(":");
        if (parts.length < 4 || !parts[1].equals("move") || !parts[3].equals("boss")) return null;
        String target = ShowdownPool.target(parts[2]);
        if (target == null || !(target.startsWith("normal/") || target.startsWith("any/") || target.startsWith("adjacentFoe/")
                || target.startsWith("randomNormal/"))) return null;
        for (Trainer t : trainers) {
            if (t.battle != null && t.battle.getBattleId().toString().equals(parts[0])) return t.player;
        }
        return null;
    }

    public List<Entity> foes() {
        List<Entity> out = new ArrayList<>();
        for (Trainer t : trainers) {
            Entity foe = t.live() ? active(t) : null;
            if (foe != null) out.add(foe);
        }
        return out;
    }

    private static @Nullable Entity active(Trainer t) {
        for (ActiveBattlePokemon active : t.side.getActivePokemon()) {
            BattlePokemon pokemon = active.getBattlePokemon();
            PokemonEntity out = pokemon == null ? null : pokemon.getEntity();
            if (out != null && !out.isRemoved()) return out;
        }
        return null;
    }

    public void reserve(UUID player, Vec3 pokemonAt, Vec3 trainerAt) {
        spots.put(player, new Seat(pokemonAt, trainerAt));
    }

    @Nullable Vec3 sendOutAt(PokemonBattle battle, BattleActor actor) {
        for (Trainer t : trainers) {
            if (t.battle != battle || t.side != actor) continue;
            Seat seat = spots.get(t.player);
            return seat == null ? null : seat.pokemon();
        }
        return null;
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void watch() {
        for (Trainer t : trainers) {
            Entity out = t.live() ? active(t) : null;
            if (out != t.watched) {
                t.watched = out;
                t.watchedAt = out == null ? null : out.position();
                t.watchedTicks = 0;
                if (out != null) look(t, out, "sent out");
                continue;
            }
            if (out == null || t.watchedAt == null) continue;
            if (++t.watchedTicks == WATCH_SETTLED) look(t, out, "settled");
            if (out.position().distanceTo(t.watchedAt) > WATCH_MOVE) {
                look(t, out, "moved from " + fmt(t.watchedAt));
                t.watchedAt = out.position();
            }
        }
    }

    private void look(Trainer t, Entity out, String what) {
        AABB box = out.getBoundingBox();
        double reach = out.getBbWidth() * BODY_REACH;
        List<String> inside = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            Vec3 eye = player.getEyePosition();
            if (box.contains(eye)) inside.add(player.getName().getString() + " (in its hitbox)");
            else if (flat(eye, out.position()) < reach && eye.y < box.maxY + 1) inside.add(player.getName().getString() + " (within its body's reach)");
        }
        List<String> overlaps = new ArrayList<>();
        if (box.intersects(entity.getBoundingBox())) overlaps.add("the boss");
        for (Trainer o : trainers) {
            Entity other = o == t || !o.live() ? null : active(o);
            if (other != null && box.intersects(other.getBoundingBox())) overlaps.add(o.name + "'s " + other.getName().getString());
        }
        boolean blocks = !level.noBlockCollision(out, box);
        String extra = out instanceof PokemonEntity pokemon ? ", beam " + pokemon.getBeamMode() + ", busy " + pokemon.isBusy() + ", no AI " + pokemon.isNoAi() : "";
        Wormholes.LOGGER.info("[boss] {}'s {} #{} {} at {}: hitbox {} by {}, invisible {}{}, in blocks {}, overlaps {}, players near its body {}",
                t.name, out.getName().getString(), out.getId(), what, fmt(out.position()), String.format("%.1f", out.getBbWidth()),
                String.format("%.1f", out.getBbHeight()), out.isInvisible(), extra, blocks, overlaps, inside);
    }

    private static String fmt(Vec3 at) {
        return String.format("(%.2f, %.2f, %.2f)", at.x, at.y, at.z);
    }

    @Nullable PokemonBattle battleOf(UUID player) {
        Trainer t = trainer(player);
        return t != null && t.live() ? t.battle : null;
    }

    boolean owns(PokemonBattle battle) {
        for (Trainer t : trainers) {
            if (t.battle == battle) return true;
        }
        return false;
    }

    /** A LegendWave has its place in the battles, or null again: no HP bar or health updates of its own meanwhile. */
    void away(@Nullable LegendWave wave) {
        this.wave = wave;
        for (Trainer t : trainers) t.shownHp = -1;
    }

    private @Nullable Trainer trainer(UUID player) {
        for (Trainer t : trainers) {
            if (t.player.equals(player)) return t;
        }
        return null;
    }

    @Nullable ServerPlayer online(Trainer t) {
        return level.getServer().getPlayerList().getPlayer(t.player);
    }

    static boolean send(@Nullable ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || !ServerPlayNetworking.canSend(player, payload.type())) return false;
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    private static void change(StringBuilder changes, String stat, int by) {
        changes.append(changes.isEmpty() ? "" : ",").append(stat).append(':').append(by);
    }

    private @Nullable BossDef.Form currentForm() {
        return form >= 0 && form < def.forms.size() ? def.forms.get(form) : null;
    }

    private @Nullable BossDef.Variant currentVariant() {
        return def.variant(variant);
    }

    public @Nullable ResourceLocation music() {
        BossDef.Form f = currentForm();
        if (f != null && f.music() != null) return f.music();
        BossDef.Variant v = currentVariant();
        if (v != null && v.music() != null) return v.music();
        return def.music;
    }

    private @Nullable List<String> moves() {
        BossDef.Form f = currentForm();
        if (f != null && !f.moves().isEmpty()) return f.moves();
        BossDef.Variant v = currentVariant();
        if (v != null && !v.moves().isEmpty()) return v.moves();
        return def.moves.isEmpty() ? null : def.moves;
    }

    private @Nullable BossDef.Ultimate ultimate() {
        BossDef.Form f = currentForm();
        if (f != null && f.ultimate() != null) return f.ultimate();
        return def.ultimate;
    }

    int maxTrainers() {
        return Math.max(1, def.maxTrainers > 0 ? def.maxTrainers : BossConfig.get().maxTrainers);
    }

    private int target(int trainerCount) {
        int n = Mth.clamp(trainerCount, 1, maxTrainers());
        double multiplier = def.scaling != null ? def.scaling[Math.min(n, def.scaling.length) - 1] : perTrainer * n;
        return (int) Math.max(1, Math.round(base * multiplier));
    }

    private int catchAt() {
        return Math.max(1, (int) Math.floor(BossConfig.get().catchBelow * max));
    }

    private int alive() {
        int alive = 0;
        for (Trainer t : trainers) {
            if (t.battle == null || t.live()) alive++;
        }
        return alive;
    }

    public BattleStartResult join(ServerPlayer player, @Nullable UUID lead) {
        if (!ShowdownPool.available() || phase != Phase.FIGHT) return new ErroredBattleStart();
        if (player.isSpectator() || !player.isAlive() || player.level() != level) return new ErroredBattleStart();
        if (fighting(player) || BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return refuse(player, "in_battle", "You're already in a battle.");
        }
        int cap = maxTrainers();
        if (trainers.size() >= cap) return refuse(player, "full", "%s already has %s trainers fighting it.", name(), cap);
        List<BattlePokemon> team = new ArrayList<>(PlayerExtensionsKt.party(player).toBattleTeam(false, false, lead));
        team.sort(Comparator.comparing((BattlePokemon p) -> p.getHealth() <= 0));
        if (team.isEmpty() || team.getFirst().getHealth() <= 0) {
            return refuse(player, "no_pokemon", "You have no Pokémon able to battle.");
        }
        Pokemon copy = entity.getPokemon().clone(true, player.registryAccess());
        copy.setState(new SentOutState(entity));
        Bosses.track(copy, this);
        Bosses.link(copy, entity);
        PlayerBattleActor side = new PlayerBattleActor(player.getUUID(), team);
        ResourceLocation music = music();
        side.setBattleTheme(music != null ? music : entity.getBattleTheme());
        int skill = def.aiSkill >= 0 ? def.aiSkill : BossConfig.get().aiSkill;
        BattleAI ai = rules != null ? rules.attacks(new StrongBattleAI(skill)) : new StrongBattleAI(skill);
        float usual = Cobblemon.INSTANCE.getConfig().getDefaultFleeDistance();
        BossActor actor = new BossActor(new BattlePokemon(copy, copy, new ArrayList<>(), new ArrayList<>()),
                rules != null ? rules.fleeDistance(usual) : usual, ai);
        actor.lopsided = rules != null && rules.lopsided();
        Trainer trainer = new Trainer(player, copy, actor, side);
        trainers.add(trainer);
        rescale();
        ShowdownPool.join(copy.getUuid().toString(), id);
        BattleStartResult result = BattleRegistry.startBattle(actor.lopsided ? BattleFormat.Companion.getGEN_9_DOUBLES() : BattleFormat.Companion.getGEN_9_SINGLES(),
                new BattleSide(side), new BattleSide(actor), true);
        if (!(result instanceof SuccessfulBattleStart started)) {
            trainers.remove(trainer);
            ShowdownPool.leave(copy.getUuid().toString());
            Bosses.forget(copy);
            rescale();
            return result;
        }
        trainer.battle = started.getBattle();
        Bosses.bind(trainer.battle, this);
        send(player, new BossBanPayload(def.bannedMoves));
        entity.setBattleId(null);
        int attached = ShowdownPool.attach(trainer.battle.getBattleId().toString());
        if (attached != 1) Wormholes.LOGGER.warn("[boss] {}'s battle has {} Pokemon on the shared HP (expected 1)", trainer.name, attached);
        Wormholes.LOGGER.info("[boss] {} joined {} ({} trainers): {}/{} HP, battle {}", trainer.name, name().getString(), trainers.size(),
                hp, max, trainer.battle.getBattleId());
        if (rules != null) rules.joined(this, player, trainer.battle);
        return result;
    }

    private BattleStartResult refuse(ServerPlayer player, String key, String fallback, Object... args) {
        player.sendSystemMessage(Component.translatableWithFallback("hyperspace_wormholes.boss." + key, fallback, args)
                .withStyle(ChatFormatting.RED));
        return new ErroredBattleStart();
    }

    void rescale() {
        int newMax = target(alive());
        if (newMax != max) {
            int before = max;
            hp = Mth.clamp((int) Math.round(hp * (double) newMax / max), 1, newMax);
            max = newMax;
            Wormholes.LOGGER.info("[boss] {} rescaled for {} trainers: max {} -> {}, HP {}/{} ({}%)", name().getString(), alive(),
                    before, max, hp, max, percent());
        }
        push();
    }

    private void push() {
        if (!trainers.isEmpty() || pooled) {
            ShowdownPool.pool(id, hp, max);
            if (!pooled) setupPool();
            pooled = true;
        }
        shown = hp;
        mirror();
        sendHealth();
        save();
    }

    private void setupPool() {
        List<String> moves = moves();
        ShowdownPool.setup(id, moves == null ? null : String.join(",", moves));
        ShowdownPool.ban(id, def.bannedMoves);
        if (rules != null && rules.lopsided()) ShowdownPool.lopsided(id);
        StringBuilder changes = new StringBuilder();
        for (int i = 0; i < stages.length; i++) {
            if (stages[i] != 0) change(changes, BossDef.STATS.get(i), stages[i]);
        }
        if (!changes.isEmpty()) ShowdownPool.boost(id, changes.toString());
    }

    private void mirror() {
        entity.getPokemon().setCurrentHealth(shown);
        for (Trainer t : trainers) t.copy.setCurrentHealth(shown);
    }

    private void reveal() {
        if (shown == hp) return;
        shown = hp;
        mirror();
    }

    static boolean busy(Trainer t) {
        return !t.battle.getDispatches().isEmpty() || !t.battle.getDispatchResult().canProceed();
    }

    private void sendHealth() {
        if (wave != null) return;
        for (Trainer t : trainers) {
            if (!t.live() || !t.battle.getStarted() || t.battle.getTurn() < 1) continue;
            t.playing = busy(t);
            if (t.shownHp == shown) continue;
            t.shownHp = shown;
            List<ActiveBattlePokemon> active = t.actor.getActivePokemon();
            if (active.isEmpty() || active.getFirst().getBattlePokemon() == null) continue;
            String pnx = active.getFirst().getPNX();
            t.battle.sendSidedUpdate(t.actor, new BattleHealthChangePacket(pnx, shown, (float) max),
                    new BattleHealthChangePacket(pnx, shown / (float) max, null), false);
        }
    }

    void tick() {
        ticks++;
        if (pooled && ShowdownPool.reinstalled()) {
            ShowdownPool.pool(id, hp, max);
            setupPool();
        }
        if (pooled && !trainers.isEmpty()) {
            ShowdownPool.State now = ShowdownPool.read(id);
            if (now == null || now.max() != max) {
                ShowdownPool.pool(id, hp, max);
            } else {
                System.arraycopy(now.stages(), 0, stages, 0, stages.length);
                if (now.hp() != hp) {
                    hp = now.hp();
                    save();
                }
            }
        }
        dropFinished();
        try {
            round();
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("[boss] {}'s round failed", name().getString(), e);
        }
        reveal();
        upkeep();
        watch();
        echo();
        sendHealth();
        hud();
        if (phase == Phase.CATCH) {
            phaseTicks++;
            if (rules == null && (trainers.isEmpty() || phaseTicks > BossConfig.get().catchSeconds * 20)) finishCatch();
        } else if (trainers.isEmpty() && !paused) {
            emptyTicks++;
            if (emptyTicks == BossConfig.get().emptyHealSeconds * 20 && hp < max) {
                hp = max;
                push();
            }
        } else {
            emptyTicks = 0;
        }
    }

    private void dropFinished() {
        boolean changed = false;
        for (int i = 0; i < trainers.size(); i++) {
            Trainer t = trainers.get(i);
            if (t.battle == null) continue;
            ServerPlayer player = online(t);
            if (!t.battle.getEnded() && (player == null || player.isRemoved())) t.battle.stop();
            if (!t.battle.getEnded()) continue;
            boolean caught = t.battle.getEndResult() == BattleEndResult.CAPTURE;
            trainers.remove(i--);
            ShowdownPool.leave(t.copy.getUuid().toString());
            Bosses.forget(t.copy);
            Bosses.unbind(t.battle);
            hideHud(t, player);
            changed = true;
            Wormholes.LOGGER.info("[boss] {} {} {} ({} trainers left)", t.name, caught ? "caught" : "left the fight against",
                    name().getString(), trainers.size());
        }
        if (changed && trainers.isEmpty()) push();
        else if (changed) rescale();
    }

    private void round() {
        if (trainers.isEmpty() || !pooled) {
            idleTicks = 0;
            return;
        }
        boolean idle = true;
        for (Trainer t : trainers) {
            if (t.live() && busy(t)) idle = false;
        }
        idleTicks = idle ? idleTicks + 1 : 0;
        if (autoplay) autoplay();
        Map<String, String[]> states = ShowdownPool.status(id);
        boolean held = false, choosing = false, done = false;
        for (Trainer t : trainers) {
            if (!t.live()) continue;
            String battleId = t.battle.getBattleId().toString();
            String[] state = states.get(battleId);
            if (state == null) continue;
            if (state.length > 3) {
                Wormholes.LOGGER.error("[boss] Showdown failed in {}'s battle: {}", t.name, ShowdownPool.error(battleId));
                t.battle.stop();
                continue;
            }
            switch (state[1]) {
                case "held" -> held = true;
                case "wait" -> choosing = true;
                case "done" -> done = true;
                default -> {}
            }
        }
        showWaits(paused || !idle || held || choosing || done);
        if (paused || idleTicks < SETTLE_TICKS) return;
        if (phase == Phase.FIGHT) {
            BossDef.Form next = rules != null && rules.passesForms() && hp <= catchAt() ? null : nextForm();
            if (next != null) {
                changeForm(next);
                return;
            }
            if (hp <= catchAt()) {
                defeat();
                return;
            }
        }
        if (choosing) return;
        if (held) {
            String step = ShowdownPool.step(id);
            aiming = aimOf(step);
            idleTicks = 0;
            Wormholes.LOGGER.info("[boss] {} step {} ({}/{} HP)", name().getString(), step, hp, max);
            return;
        }
        if (done) {
            arm();
            aiming = null;
            int ended = ShowdownPool.finish(id);
            idleTicks = 0;
            Wormholes.LOGGER.info("[boss] {} round over in {} battles ({}/{} HP, stages {})", name().getString(), ended, hp, max, stageMap());
            return;
        }
        if (!boosted && phase == Phase.FIGHT && ready()) {
            boosted = true;
            powerUp(def.boosts);
        }
        decide();
    }

    private void showWaits(boolean playing) {
        for (Trainer t : trainers) {
            int state = !playing && picked(t) && trainers.stream().anyMatch(o -> o != t && o.live() && !picked(o))
                    ? BossWaitPayload.PICKS : BossWaitPayload.NONE;
            if (state == t.waitShown || !send(online(t), new BossWaitPayload(state))) continue;
            t.waitShown = state;
            Wormholes.LOGGER.info("[boss] {}'s menu: {}", t.name, state == BossWaitPayload.NONE ? "no waiting line" : "waiting for the others to pick");
        }
    }

    private static boolean picked(Trainer t) {
        return t.live() && !t.side.getMustChoose() && !t.side.getResponses().isEmpty();
    }

    private boolean ready() {
        boolean any = false;
        for (Trainer t : trainers) {
            if (!t.live()) continue;
            if (!t.actor.ready() || t.side.getMustChoose() || t.side.getResponses().isEmpty()) return false;
            any = true;
        }
        return any;
    }

    private void decide() {
        if (!ready()) return;
        List<Trainer> live = trainers.stream().filter(Trainer::live).toList();
        if (phase != Phase.FIGHT) {
            for (Trainer t : live) t.actor.decide(PassActionResponse.INSTANCE);
            return;
        }
        BossDef.Ultimate ultimate = ultimate();
        if (ultimateDue(ultimate)) {
            Map<Trainer, ShowdownActionResponse> uses = new LinkedHashMap<>();
            for (Trainer t : live) {
                ShowdownActionResponse use = t.actor.ultimate(ultimate.move(), ultimate.zMove());
                if (use != null) uses.put(t, use);
            }
            if (!uses.isEmpty()) {
                if (ultimateUsed) ultimateAgainUsed = true;
                ultimateUsed = true;
                save();
                ShowdownPool.together(id, false);
                for (Trainer t : live) t.actor.decide(uses.getOrDefault(t, PassActionResponse.INSTANCE));
                strike(uses.keySet().iterator().next());
                Wormholes.LOGGER.info("[boss] {} uses its ultimate {} on {} trainers ({}/{} HP)", name().getString(), ultimate.move(),
                        uses.size(), hp, max);
                return;
            }
            Wormholes.LOGGER.warn("[boss] {} can't use its ultimate {} in any battle", name().getString(), ultimate.move());
        }
        int from = -1;
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i).player.equals(last)) from = i;
        }
        Trainer best = null;
        ShowdownActionResponse bestPlan = PassActionResponse.INSTANCE;
        double bestScore = -1;
        StringBuilder scores = new StringBuilder();
        for (int step = 1; step <= live.size(); step++) {
            Trainer t = live.get(Math.floorMod(from + step, live.size()));
            ShowdownActionResponse plan = t.actor.plan();
            double score = -1;
            String moveName = "pass";
            if (plan instanceof MoveActionResponse move) {
                moveName = move.getMoveName();
                double damage = ShowdownPool.score(t.battle.getBattleId().toString(), moveName);
                score = damage > 0 ? damage : STATUS_SCORE;
            }
            scores.append(t.name).append('=').append(moveName).append(String.format("(%.2f) ", score));
            if (score > bestScore + 1e-6) {
                best = t;
                bestPlan = plan;
                bestScore = score;
            }
        }
        if (best != null && bestPlan instanceof MoveActionResponse move && live.size() > 1 && spread(move.getMoveName())) {
            Map<Trainer, ShowdownActionResponse> uses = new LinkedHashMap<>();
            for (Trainer t : live) {
                ShowdownActionResponse use = t == best ? bestPlan : t.actor.use(move.getMoveName());
                if (use != null) uses.put(t, use);
            }
            ShowdownPool.together(id, uses.size() > 1);
            for (Trainer t : live) t.actor.decide(uses.getOrDefault(t, PassActionResponse.INSTANCE));
            last = best.player;
            strike(best);
            Wormholes.LOGGER.info("[boss] {} uses {} on every trainer ({} battles, turn {}; {})", name().getString(), move.getMoveName(),
                    uses.size(), best.battle.getTurn(), scores.toString().trim());
            return;
        }
        for (Trainer t : live) t.actor.decide(t == best ? bestPlan : PassActionResponse.INSTANCE);
        if (best != null) {
            last = best.player;
            strike(best);
            Wormholes.LOGGER.info("[boss] {} attacks {} with {} (turn {}; {})", name().getString(), best.name,
                    bestPlan instanceof MoveActionResponse move ? move.getMoveName() : "pass", best.battle.getTurn(), scores.toString().trim());
        }
    }

    private void strike(Trainer t) {
        struck = t.player;
        struckAt = ticks;
    }

    private static boolean spread(String move) {
        String target = ShowdownPool.target(move);
        return target.startsWith("allAdjacentFoes/") || target.startsWith("allAdjacent/");
    }

    private void arm() {
        BossDef.Ultimate ultimate = ultimate();
        boolean want = ultimateDue(ultimate);
        List<String> moves = moves();
        boolean missing = want && (moves == null || !moves.contains(ultimate.move()));
        if (missing && !armed) {
            ShowdownPool.arm(id, ultimate.move());
            armed = true;
        } else if (!missing && armed) {
            ShowdownPool.arm(id, null);
            armed = false;
        }
    }

    private boolean ultimateDue(@Nullable BossDef.Ultimate ultimate) {
        if (ultimate == null || phase != Phase.FIGHT) return false;
        if (!ultimateUsed) return hp <= ultimate.hpBelow() * max;
        return hard && !ultimateAgainUsed && ultimate.hardAgainBelow() > 0 && hp <= ultimate.hardAgainBelow() * max;
    }

    private @Nullable BossDef.Form nextForm() {
        BossDef.Form next = null;
        for (int i = form + 1; i < def.forms.size(); i++) {
            if (hp > def.forms.get(i).hpBelow() * max) continue;
            next = def.forms.get(i);
            if (rules == null || !rules.passesForms()) break;
        }
        return next;
    }

    private void changeForm(BossDef.Form next) {
        form = def.forms.indexOf(next);
        save();
        int skipped = ShowdownPool.cancel(id, true);
        Wormholes.LOGGER.info("[boss] {} at {}/{} HP ({}%) changes form to {} ({} queued actions skipped on both sides)", name().getString(), hp, max,
                percent(), next.name() != null ? next.name() : next.species(), skipped);
        if (rules != null && rules.formChange(this, next)) {
            paused = true;
            return;
        }
        applyForm(next);
        powerUp(next.boosts());
    }

    public void pause() {
        paused = true;
    }

    public boolean hard() {
        return hard;
    }

    /** Its HP pool after its Pokemon changed (Bosses.harden raising its level), keeping the HP's fraction. */
    public void refreshBase() {
        double fraction = hp / (double) max;
        base = Math.max(1, entity.getPokemon().getStat(Stats.HP));
        max = target(Math.max(1, alive()));
        hp = Mth.clamp((int) Math.round(fraction * max), 1, max);
        push();
    }

    public void setHard(boolean hard) {
        this.hard = hard;
        save();
    }

    public void resume() {
        paused = false;
        idleTicks = 0;
    }

    public void nextTurns() {
        resume();
        for (Trainer t : trainers) {
            if (t.live()) ShowdownPool.turn(t.battle.getBattleId().toString());
        }
    }

    public void applyForm(BossDef.Form next) {
        shownForm = def.forms.indexOf(next);
        double fraction = hp / (double) max;
        List<Pokemon> all = new ArrayList<>();
        for (Trainer t : trainers) all.add(t.copy);
        if (!next.properties().isBlank()) {
            PokemonProperties properties = PokemonProperties.Companion.parse(next.properties());
            properties.apply(entity);
            for (Pokemon pokemon : all) properties.apply(pokemon);
        }
        all.add(entity.getPokemon());
        if (next.heldItem() != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(next.heldItem()));
            if (stack.isEmpty()) {
                Wormholes.LOGGER.warn("[boss] no item {} for {}'s form", next.heldItem(), name().getString());
            } else {
                for (Pokemon pokemon : all) pokemon.swapHeldItem(stack.copy(), false, false);
            }
        }
        entity.refreshDimensions();
        base = Math.max(1, entity.getPokemon().getStat(Stats.HP));
        max = target(alive());
        hp = Mth.clamp((int) Math.round(fraction * max), 1, max);
        JsonObject spec = new JsonObject();
        if (next.species() != null) spec.addProperty("species", next.species());
        if (next.level() > 0) spec.addProperty("level", next.level());
        if (next.ability() != null) spec.addProperty("ability", next.ability());
        if (next.item() != null) spec.addProperty("item", next.item());
        if (next.zItem() != null) spec.addProperty("zitem", next.zItem());
        if (!next.moves().isEmpty()) {
            JsonArray moves = new JsonArray();
            next.moves().forEach(moves::add);
            spec.add("moves", moves);
        }
        ShowdownPool.pool(id, hp, max);
        String result = ShowdownPool.transform(id, spec.toString());
        armed = false;
        for (Trainer t : trainers) t.shownHp = -1;
        push();
        Wormholes.LOGGER.info("[boss] {} took its form {}: {}/{} HP (base {}), Showdown {}", name().getString(), spec, hp, max, base, result);
    }

    public void powerUp(Map<String, Integer> boosts) {
        if (boosts.isEmpty()) return;
        StringBuilder changes = new StringBuilder();
        boosts.forEach((stat, by) -> {
            if (stat.equals("highest") && hard) stat = "all";
            if (stat.equals("all")) {
                for (Stats s : BATTLE_STATS) change(changes, s.getShowdownId(), by);
                return;
            }
            if (stat.equals("second_lowest")) {
                // the stat (or stats, on a tie) with the second lowest base value, HP aside
                Map<Stat, Integer> base = entity.getPokemon().getForm().getBaseStats();
                int[] values = new int[BATTLE_STATS.length];
                for (int i = 0; i < values.length; i++) values[i] = base.getOrDefault(BATTLE_STATS[i], 0);
                int[] sorted = values.clone();
                java.util.Arrays.sort(sorted);
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == sorted[1]) change(changes, BATTLE_STATS[i].getShowdownId(), by);
                }
                return;
            }
            if (!stat.equals("highest")) {
                change(changes, stat, by);
                return;
            }
            // "highest": the stat (or stats, on a tie) with the highest base value in the form it has now, HP aside
            Map<Stat, Integer> base = entity.getPokemon().getForm().getBaseStats();
            int top = 0;
            for (Stats s : BATTLE_STATS) top = Math.max(top, base.getOrDefault(s, 0));
            for (Stats s : BATTLE_STATS) {
                if (top > 0 && base.getOrDefault(s, 0) == top) change(changes, s.getShowdownId(), by);
            }
        });
        if (changes.isEmpty()) return;
        String now = ShowdownPool.boost(id, changes.toString());
        if (now != null && !now.isEmpty()) ShowdownPool.stages(now, stages);
        broadcast(Component.translatableWithFallback("hyperspace_wormholes.boss.powering_up", "%s is powering up!", name())
                .withStyle(ChatFormatting.GOLD));
        Wormholes.LOGGER.info("[boss] {} powers up {}: stages {}", name().getString(), changes, stageMap());
    }

    public void broadcast(Component line) {
        Bosses.quietly(() -> {
            for (Trainer t : trainers) {
                if (t.live()) t.battle.broadcastChatMessage(line);
            }
        });
    }

    void forward(PokemonBattle from, Component message) {
        heard.add(new Line(from, message, ticks));
    }

    private void echo() {
        if (!heard.isEmpty()) Bosses.quietly(this::sendHeard);
    }

    private void sendHeard() {
        for (Line line : heard) {
            if (line.done || ticks - line.at < ECHO_TICKS) continue;
            line.done = true;
            for (Trainer t : trainers) {
                if (!t.live() || t.battle == line.from || echoed(t, line)) continue;
                t.side.sendMessage(line.message);
                line.sentTo.add(t.player);
            }
        }
        heard.removeIf(line -> line.done && ticks - line.at > 2 * ECHO_TICKS);
    }

    private boolean echoed(Trainer t, Line line) {
        for (Line other : heard) {
            if (other == line || Math.abs(other.at - line.at) > ECHO_TICKS || !other.text.equals(line.text)) continue;
            if (other.from == t.battle || other.sentTo.contains(t.player)) return true;
        }
        return false;
    }

    private void autoplay() {
        if (idleTicks < AUTOPLAY_TICKS) return;
        for (Trainer t : trainers) {
            if (!t.live() || !t.side.getMustChoose() || t.player.equals(autoplayExcept)) continue;
            ShowdownActionRequest request = t.side.getRequest();
            if (request == null) continue;
            StrongBattleAI ai = new StrongBattleAI(3);
            try {
                List<ShowdownActionResponse> responses = request.iterate(t.side.getActivePokemon(),
                        (active, moveset, forceSwitch) -> {
                            ShowdownActionResponse response = ai.choose(active, t.battle, t.side.getSide(), moveset, forceSwitch);
                            if (forceSwitch && response instanceof PassActionResponse) return new DefaultActionResponse();
                            return response instanceof MoveActionResponse move && move.getGimmickID() != null
                                    ? new MoveActionResponse(move.getMoveName(), move.getTargetPnx(), null) : response;
                        });
                t.side.setActionResponses(responses);
                Wormholes.LOGGER.info("[boss] autoplay chose {} for {}", responses, t.name);
            } catch (RuntimeException e) {
                Wormholes.LOGGER.warn("[boss] autoplay couldn't choose for {}", t.name, e);
            }
        }
    }

    private void defeat() {
        int skipped = ShowdownPool.cancel(id, false);
        phase = Phase.CATCH;
        phaseTicks = 0;
        Wormholes.LOGGER.info("[boss] {} beaten at {}/{} with {} trainers ({} queued boss moves skipped)", name().getString(), hp, max,
                trainers.size(), skipped);
        if (rules != null && rules.defeated(this)) {
            save();
            return;
        }
        PokemonProperties.Companion.parse("uncatchable=false").apply(entity);
        if (def.defeatedProperties != null) PokemonProperties.Companion.parse(def.defeatedProperties).apply(entity);
        save();
    }

    private void finishCatch() {
        for (Trainer t : new ArrayList<>(trainers)) {
            if (t.live()) t.battle.stop();
        }
        Wormholes.LOGGER.info("[boss] {} catch phase over, removed", name().getString());
        entity.discard();
    }

    private void upkeep() {
        if (entity.getPokemon().getCurrentHealth() != shown) entity.getPokemon().setCurrentHealth(shown);
        if (rules != null) return;
        if (entity.getBattleId() != null) entity.setBattleId(null);
        if (entity.isBusy()) return;
        if (entity.position().distanceToSqr(home) > 0.25) entity.moveTo(home.x, home.y, home.z, entity.getYRot(), 0f);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    void restoreLook() {
        if (entity.isRemoved()) return;
        entity.setInvisible(false);
        entity.setSilent(false);
        entity.setBeamMode(0);
        entity.setPhasingTargetId(-1);
    }

    private Hud hudNow() {
        return new Hud(hudName(), entity.getPokemon().getLevel(), Mth.clamp(shown / (float) max, 0f, 1f), stageMap(), "");
    }

    private Map<String, Integer> stageMap() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < stages.length; i++) {
            if (stages[i] != 0) out.put(BossDef.STATS.get(i), stages[i]);
        }
        return out;
    }

    private String hudName() {
        BossDef.Form f = shownForm >= 0 && shownForm < def.forms.size() ? def.forms.get(shownForm) : null;
        if (f != null && f.name() != null) return f.name();
        BossDef.Variant v = currentVariant();
        if (v != null && v.name() != null) return v.name();
        if (def.name != null) return def.name;
        Component species = entity.getPokemon().getSpecies().getTranslatedName();
        return species.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : species.getString();
    }

    private void hud() {
        if (wave != null) {
            for (Trainer t : trainers) {
                if (t.shown != null && send(online(t), new BossHudPayload(entity.getId(), "", 0, 0f, new HashMap<>(), false, ""))) t.shown = null;
            }
            return;
        }
        Hud now = null;
        for (Trainer t : trainers) {
            if (!t.live()) continue;
            if (now == null) {
                now = hudNow();
                if (!now.equals(logged)) {
                    logged = now;
                    Wormholes.LOGGER.info("[boss] HUD {} Lv.{} {}% stages {}", now.name(), now.level(), Math.round(now.hp() * 1000) / 10.0, now.stages());
                }
            }
            Hud mine = new Hud(now.name(), now.level(), now.hp(), now.stages(), status(t));
            if (mine.equals(t.shown)) continue;
            boolean newStatus = t.shown == null || !mine.status().equals(t.shown.status());
            if (!send(online(t), new BossHudPayload(entity.getId(), mine.name(), mine.level(), mine.hp(),
                    new HashMap<>(mine.stages()), true, mine.status()))) continue;
            if (newStatus) Wormholes.LOGGER.info("[boss] HUD status for {}: '{}'", t.name, mine.status());
            t.shown = mine;
        }
    }

    private static String status(Trainer t) {
        PersistentStatusContainer container = t.copy.getStatus();
        String out = container == null ? "" : container.getStatus().getShowdownName();
        List<BattlePokemon> own = t.actor.getPokemonList();
        if (!own.isEmpty()) {
            Collection<BattleContext> volatiles = own.getFirst().getContextManager().get(BattleContext.Type.VOLATILE);
            if (volatiles != null && volatiles.stream().anyMatch(c -> "confusion".equals(c.getId()))) {
                out = out.isEmpty() ? "confusion" : out + ",confusion";
            }
        }
        return out;
    }

    private void hideHud(Trainer t, @Nullable ServerPlayer player) {
        send(player, new BossBanPayload(List.of()));
        if (t.waitShown != BossWaitPayload.NONE) send(player, new BossWaitPayload(BossWaitPayload.NONE));
        t.waitShown = BossWaitPayload.NONE;
        if (t.shown == null) return;
        t.shown = null;
        send(player, new BossHudPayload(entity.getId(), "", 0, 0f, new HashMap<>(), false, ""));
    }

    int percent() {
        return hp >= max ? 100 : (int) Math.ceil(hp * 100.0 / max);
    }

    public Component name() {
        return entity.getPokemon().getSpecies().getTranslatedName();
    }

    void save() {
        if (rules != null && !rules.saved()) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("def", def.id.toString());
        if (variant != null) tag.putString("variant", variant);
        tag.putInt("form", form);
        tag.putBoolean("ultimate", ultimateUsed);
        tag.putBoolean("hard", hard);
        tag.putBoolean("ultimateAgain", ultimateAgainUsed);
        tag.putDouble("perTrainer", perTrainer);
        tag.putDouble("fraction", hp / (double) max);
        tag.putBoolean("catching", phase == Phase.CATCH);
        tag.putDouble("x", home.x);
        tag.putDouble("y", home.y);
        tag.putDouble("z", home.z);
        tag.putFloat("yaw", yaw);
        entity.getPokemon().getPersistentData().put(DATA, tag);
    }

    void restored(boolean ultimate, boolean ultimateAgain, boolean hard) {
        ultimateUsed = ultimate;
        ultimateAgainUsed = ultimateAgain;
        this.hard = hard;
        boosted = true;
    }

    void close() {
        for (Trainer t : trainers) {
            if (t.live()) t.battle.stop();
            ShowdownPool.leave(t.copy.getUuid().toString());
            Bosses.forget(t.copy);
            if (t.battle != null) Bosses.unbind(t.battle);
            hideHud(t, online(t));
        }
        trainers.clear();
        if (pooled) ShowdownPool.drop(id);
        pooled = false;
        Bosses.forget(entity.getPokemon());
    }

    public void endAllAsWins() {
        for (Trainer t : trainers) {
            if (t.live()) t.battle.writeShowdownAction(">forcewin " + t.side.getShowdownId());
        }
    }

    public void setHpFraction(double fraction) {
        if (wave != null) {
            wave.setHpFraction(fraction);
            return;
        }
        hp = Mth.clamp((int) Math.round(fraction * max), 1, max);
        push();
    }

    String describe() {
        StringBuilder names = new StringBuilder();
        for (Trainer t : trainers) {
            if (!names.isEmpty()) names.append(", ");
            names.append(t.name);
            if (t.player.equals(last)) names.append('*');
        }
        BossDef.Form f = currentForm();
        return String.format("%s (%s%s%s) at %.0f %.0f %.0f: %s/%s HP (%s%%), stages %s, %s trainers [%s], %s%s%s", name().getString(),
                def.id, variant != null ? " " + variant : "", f != null ? ", form " + Objects.requireNonNullElse(f.name(), f.species()) : "",
                home.x, home.y, home.z, hp, max, percent(), stageMap(), trainers.size(), names, phase, paused ? ", paused" : "",
                ultimateUsed ? ", ultimate used" : "");
    }
}
