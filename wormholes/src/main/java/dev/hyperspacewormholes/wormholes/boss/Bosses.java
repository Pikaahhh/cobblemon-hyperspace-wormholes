package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import kotlin.Unit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of every boss Pokemon (Boss) on the server: spawns them (/boss spawn, or spawn and adopt from other code,
 * each from a BossDef), ticks them, restores them after a restart and answers the battle mixins.
 * A successful catch swaps the Poke Ball's target for a fresh catchable copy, so the real boss stays for everyone else.
 * Every line one trainer's battle shows is forwarded to the boss's other battles (forward, from BossLogMixin), apart
 * from turn numbers and lines every battle already gets.
 */
public final class Bosses {
    private static final Map<UUID, Boss> BOSSES = new LinkedHashMap<>();
    private static final Map<Pokemon, Boss> POKEMON = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<EmptyPokeBallEntity, Boss> BALLS = new IdentityHashMap<>();
    private static final Map<UUID, Entity> LINKS = new ConcurrentHashMap<>();
    private static final Map<PokemonBattle, Boss> BATTLES = new IdentityHashMap<>();
    private static final String TURN_LINE = "cobblemon.battle.turn";
    private static boolean quiet;
    private static volatile int tracked;

    private Bosses() {}

    public static void register() {
        BossConfig.load();
        BossDefs.register();
        PayloadTypeRegistry.playS2C().register(BossLinkPayload.TYPE, BossLinkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossWaitPayload.TYPE, BossWaitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossSlotsPayload.TYPE, BossSlotsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossBanPayload.TYPE, BossBanPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (Map.Entry<UUID, Entity> link : LINKS.entrySet()) {
                send(handler.getPlayer(), new BossLinkPayload(link.getKey(), link.getValue().getId()));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerEntityEvents.ENTITY_LOAD.register(Bosses::onLoad);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Boss boss : new ArrayList<>(BOSSES.values())) boss.close();
            BOSSES.clear();
            BALLS.clear();
            BATTLES.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LINKS.clear();
            POKEMON.clear();
            tracked = 0;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BossCommand.register(dispatcher));
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.LOWEST, event -> {
            Boss boss = of(event.getPokemonEntity());
            if (boss == null) return Unit.INSTANCE;
            BALLS.put(event.getPokeBallEntity(), boss);
            if (event.getCaptureResult().isSuccessfulCapture()) {
                PokemonEntity prize = prize(boss, event.getPokemonEntity());
                if (prize != null) event.getPokeBallEntity().setCapturingPokemon(prize);
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            Boss boss = of(event.getPokemon());
            if (boss == null) return Unit.INSTANCE;
            Entity owner = event.getPokeBall().getOwner();
            if (owner instanceof ServerPlayer player && boss.fighting(player)) return Unit.INSTANCE;
            event.cancel();
            if (owner instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatableWithFallback("hyperspace_wormholes.boss.battle_first",
                        "Battle %s to catch it.", boss.name()).withStyle(ChatFormatting.RED));
            }
            return Unit.INSTANCE;
        });
    }

    public static @Nullable Boss of(@Nullable Entity entity) {
        if (entity == null || entity.level().isClientSide()) return null;
        return BOSSES.get(entity.getUUID());
    }

    public static @Nullable Boss of(Pokemon pokemon) {
        if (tracked == 0) return null;
        return POKEMON.get(pokemon);
    }

    static void track(Pokemon pokemon, Boss boss) {
        POKEMON.put(pokemon, boss);
        tracked = POKEMON.size();
    }

    static void forget(Pokemon pokemon) {
        POKEMON.remove(pokemon);
        tracked = POKEMON.size();
        Entity was = LINKS.remove(pokemon.getUuid());
        if (was != null) sendAll(was, new BossLinkPayload(pokemon.getUuid(), -1));
    }

    static void link(Pokemon copy, Entity entity) {
        LINKS.put(copy.getUuid(), entity);
        sendAll(entity, new BossLinkPayload(copy.getUuid(), entity.getId()));
    }

    static void bind(PokemonBattle battle, Boss boss) {
        BATTLES.put(battle, boss);
    }

    static void unbind(PokemonBattle battle) {
        BATTLES.remove(battle);
    }

    static void quietly(Runnable run) {
        boolean was = quiet;
        quiet = true;
        try {
            run.run();
        } finally {
            quiet = was;
        }
    }

    public static void forward(PokemonBattle battle, Component message) {
        if (quiet || BATTLES.isEmpty() || battle.getTurn() < 1) return;
        Boss boss = BATTLES.get(battle);
        if (boss == null) return;
        if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(TURN_LINE)) return;
        quietly(() -> boss.forward(battle, message));
    }

    public static @Nullable Entity target(Entity bossEntity) {
        Boss boss = of(bossEntity);
        return boss == null ? null : boss.currentTarget();
    }

    public static @Nullable Vec3 sendOutAt(ActiveBattlePokemon active) {
        if (BOSSES.isEmpty()) return null;
        for (Boss boss : BOSSES.values()) {
            Vec3 at = boss.sendOutAt(active.getBattle(), active.getActor());
            if (at != null) return at;
        }
        return null;
    }

    private static void sendAll(Entity entity, BossLinkPayload payload) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) send(player, payload);
    }

    private static void send(ServerPlayer player, BossLinkPayload payload) {
        if (ServerPlayNetworking.canSend(player, BossLinkPayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }

    public static @Nullable Integer maxHealth(Pokemon pokemon) {
        Boss boss = of(pokemon);
        return boss == null ? null : boss.max;
    }

    public static @Nullable PokemonBattle ballBattle(PokemonEntity target, EmptyPokeBallEntity ball, @Nullable PokemonBattle usual) {
        Boss boss = of(target);
        if (boss == null) return usual;
        if (target.isBusy() || !(ball.getOwner() instanceof ServerPlayer thrower)) return null;
        return boss.battleOf(thrower.getUUID());
    }

    public static @Nullable PokemonEntity spawn(ServerLevel level, BossDef def, @Nullable String properties, Vec3 at, float yaw,
            double perTrainer, float scale, @Nullable Boss.Rules rules) {
        if (properties == null) properties = def.properties;
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse(properties).createEntity(level, null);
            Pokemon pokemon = entity.getPokemon();
            pokemon.setScaleModifier(scale);
            PokemonProperties.Companion.parse("uncatchable").apply(entity);
            entity.moveTo(at.x, at.y, at.z, yaw, 0f);
            entity.setYHeadRot(yaw);
            entity.setYBodyRot(yaw);
            prepare(entity);
            Boss boss = new Boss(entity, def, null, perTrainer, at, yaw, 1.0, Boss.Phase.FIGHT, -1, rules);
            BOSSES.put(entity.getUUID(), boss);
            if (rules != null && rules.startsHidden()) entity.setInvisible(true);
            level.addFreshEntity(entity);
            entity.refreshDimensions();
            return entity;
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't spawn boss '{}'", properties, e);
            return null;
        }
    }

    public static Boss adopt(PokemonEntity entity, BossDef def, @Nullable String variant, Boss.Rules rules) {
        Boss boss = BOSSES.get(entity.getUUID());
        if (boss != null) return boss;
        double perTrainer = def.hpPerTrainer > 0 ? def.hpPerTrainer : BossConfig.get().hpPerTrainer;
        boss = new Boss(entity, def, variant, perTrainer, entity.position(), entity.getYRot(), 1.0, Boss.Phase.FIGHT, -1, rules);
        BOSSES.put(entity.getUUID(), boss);
        return boss;
    }

    /** Held item that boosts each type's moves, for hard mode. */
    private static final Map<String, String> TYPE_BOOSTERS = Map.ofEntries(
            Map.entry("normal", "silk_scarf"), Map.entry("fire", "charcoal_stick"), Map.entry("water", "mystic_water"),
            Map.entry("grass", "miracle_seed"), Map.entry("electric", "magnet"), Map.entry("ice", "never_melt_ice"),
            Map.entry("fighting", "black_belt"), Map.entry("poison", "poison_barb"), Map.entry("ground", "soft_sand"),
            Map.entry("flying", "sharp_beak"), Map.entry("psychic", "twisted_spoon"), Map.entry("bug", "silver_powder"),
            Map.entry("rock", "hard_stone"), Map.entry("ghost", "spell_tag"), Map.entry("dragon", "dragon_fang"),
            Map.entry("dark", "black_glasses"), Map.entry("steel", "metal_coat"), Map.entry("fairy", "fairy_feather"));

    /**
     * Hard mode for a boss someone is fighting again: level at least 100, holding its primary type's power item, and
     * its 'highest' boosts go to every stat (Boss.setHard).
     * Call it before anyone joins. A form with its own held item (Ultra Necrozma's Ultranecrozium Z) still swaps it in.
     */
    public static void harden(Boss boss) {
        Pokemon pokemon = boss.entity().getPokemon();
        if (pokemon.getLevel() < 100) pokemon.setLevel(100);
        String type = pokemon.getPrimaryType().getShowdownId();
        String item = TYPE_BOOSTERS.get(type);
        if (item != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", item)));
            if (!stack.isEmpty()) pokemon.swapHeldItem(stack, false, false);
        }
        boss.setHard(true);
        boss.refreshBase();
    }

    public static void release(PokemonEntity entity) {
        Boss boss = BOSSES.remove(entity.getUUID());
        if (boss != null) boss.close();
    }

    private static void prepare(PokemonEntity entity) {
        entity.setNoAi(true);
        entity.setInvulnerable(true);
        entity.setPersistenceRequired();
        entity.addTag(Boss.TAG);
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon) || !entity.getTags().contains(Boss.TAG) || BOSSES.containsKey(entity.getUUID())) return;
        CompoundTag data = pokemon.getPokemon().getPersistentData().getCompound(Boss.DATA);
        if (data.isEmpty() || data.getBoolean("catching")) {
            entity.discard();
            return;
        }
        Vec3 home = new Vec3(number(data, "x", entity.getX()), number(data, "y", entity.getY()), number(data, "z", entity.getZ()));
        prepare(pokemon);
        ResourceLocation defId = ResourceLocation.tryParse(data.contains("def") ? data.getString("def") : Wormholes.MOD_ID + ":custom");
        BossDef def = defId == null ? null : BossDefs.get(defId);
        if (def == null) def = BossDef.empty(defId != null ? defId : Wormholes.id("custom"));
        String variant = data.getString("variant");
        Boss boss = new Boss(pokemon, def, variant.isEmpty() ? null : variant, number(data, "perTrainer", BossConfig.get().hpPerTrainer), home,
                (float) number(data, "yaw", entity.getYRot()), number(data, "fraction", 1.0), Boss.Phase.FIGHT,
                data.contains("form") ? data.getInt("form") : -1, null);
        boss.restored(data.getBoolean("ultimate"), data.getBoolean("ultimateAgain"), data.getBoolean("hard"));
        BOSSES.put(entity.getUUID(), boss);
    }

    private static double number(CompoundTag data, String key, double fallback) {
        return data.contains(key) ? data.getDouble(key) : fallback;
    }

    private static @Nullable PokemonEntity prize(Boss boss, PokemonEntity entity) {
        try {
            Pokemon prize = entity.getPokemon().clone(true, entity.registryAccess());
            prize.getPersistentData().remove(Boss.DATA);
            prize.setScaleModifier(1f);
            PokemonEntity stand = new PokemonEntity(entity.level(), prize, CobblemonEntities.POKEMON);
            PokemonProperties.Companion.parse("uncatchable=false").apply(stand);
            prize.heal();
            stand.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 0f);
            return stand;
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't make the caught copy of a boss", e);
            return null;
        }
    }

    private static void tick() {
        BALLS.entrySet().removeIf(ball -> {
            if (!ball.getKey().isRemoved()) return false;
            ball.getValue().entity.getBusyLocks().remove(ball.getKey());
            if (ball.getValue().entity.isInvisible()) ball.getValue().restoreLook();
            return true;
        });
        for (Boss boss : new ArrayList<>(BOSSES.values())) {
            if (boss.entity.isRemoved()) {
                BOSSES.remove(boss.entity.getUUID());
                boss.close();
                continue;
            }
            try {
                boss.tick();
            } catch (RuntimeException e) {
                Wormholes.LOGGER.error("Boss {} failed to tick", boss.name().getString(), e);
            }
        }
    }
}
