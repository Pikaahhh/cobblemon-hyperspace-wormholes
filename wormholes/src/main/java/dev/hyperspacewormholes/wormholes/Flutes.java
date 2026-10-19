package dev.hyperspacewormholes.wormholes;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.hyperspacewormholes.wormholes.ride.LegendRoar;
import dev.hyperspacewormholes.wormholes.ride.RiderArrival;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The flutes. The Poke Flute wakes your Pokemon up and never runs out. The Sun and Moon Flutes call a wild Solgaleo or
 * Lunala to take you into Ultra Space for one ride, with a cooldown. Nothing answers in Hyperspace, and the flute
 * isn't used up there.
 */
public final class Flutes {
    private static final int LEGEND_FLUTE_COOLDOWN_TICKS = 10 * 60 * 20;

    public static final Item POKE_FLUTE = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("poke_flute"),
            new PokeFlute(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item SUN_FLUTE = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("sun_flute"),
            new LegendFlute(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), "solgaleo"));
    public static final Item MOON_FLUTE = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("moon_flute"),
            new LegendFlute(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), "lunala"));

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> showCooldown(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> showCooldown(newPlayer));
    }

    public static void showCooldown(ServerPlayer player) {
        long left = Math.max(fluteCooldownLeft(player), UltraWarpRide.runCooldownLeft(player));
        if (left > 0) cool(player, left);
    }

    private static long fluteCooldownLeft(ServerPlayer player) {
        Long next = player.getAttached(Wormholes.NEXT_FLUTE);
        if (next == null) return 0;
        long left = next - player.serverLevel().getGameTime();
        return left <= 0 ? 0 : Math.min(left, LEGEND_FLUTE_COOLDOWN_TICKS);
    }

    private static void cool(ServerPlayer player, long ticks) {
        player.getCooldowns().addCooldown(SUN_FLUTE, (int) ticks);
        player.getCooldowns().addCooldown(MOON_FLUTE, (int) ticks);
    }

    private static void playTune(ServerPlayer player, float pitch) {
        SoundEvent flute = SoundEvents.NOTE_BLOCK_FLUTE.value();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), flute, SoundSource.PLAYERS, 1f, pitch);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), flute, SoundSource.PLAYERS, 0.8f, pitch * 1.26f);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), flute, SoundSource.PLAYERS, 0.6f, pitch * 1.5f);
    }

    private static final class PokeFlute extends Item {
        PokeFlute(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
            ItemStack stack = user.getItemInHand(hand);
            if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.success(stack);
            player.getCooldowns().addCooldown(this, 20);
            playTune(player, 1f);

            int woken = 0;
            Set<UUID> seen = new HashSet<>();
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon == null) continue;
                seen.add(pokemon.getUuid());
                if (wake(pokemon, pokemon.getEntity())) woken++;
            }
            for (PokemonEntity entity : player.serverLevel().getEntitiesOfClass(PokemonEntity.class,
                    player.getBoundingBox().inflate(32), e -> player.getUUID().equals(e.getPokemon().getOwnerUUID()))) {
                if (wake(entity.getPokemon(), entity) && seen.add(entity.getPokemon().getUuid())) woken++;
            }
            player.displayClientMessage(Component.translatable(woken > 0
                    ? "hyperspace_wormholes.flute.woke" : "hyperspace_wormholes.flute.none_asleep"), true);
            return InteractionResultHolder.success(stack);
        }

        private static boolean wake(Pokemon pokemon, PokemonEntity entity) {
            boolean woke = false;
            if (pokemon.getStatus() != null && pokemon.getStatus().getStatus() == Statuses.SLEEP) {
                pokemon.setStatus(null);
                woke = true;
            }
            if (entity != null && !entity.isRemoved()
                    && entity.getBrain().hasMemoryValue(CobblemonMemories.POKEMON_SLEEPING)) {
                entity.getBrain().eraseMemory(CobblemonMemories.POKEMON_SLEEPING);
                entity.getBrain().eraseMemory(CobblemonMemories.POKEMON_DROWSY);
                entity.getBrain().useDefaultActivity();
                woke = true;
            }
            return woke;
        }
    }

    private static final class LegendFlute extends Item {
        private final String species;

        LegendFlute(Properties properties, String species) {
            super(properties);
            this.species = species;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
            ItemStack stack = user.getItemInHand(hand);
            if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.success(stack);
            if (UltraWarpRules.inWarp(player) || UltraWarpRide.isRiding(player) || RiderArrival.isEmerging(player)
                    || UltraWarpRide.onCooldown(player)) {
                return InteractionResultHolder.fail(stack);
            }
            long flute = fluteCooldownLeft(player);
            if (flute > 0 || UltraWarpRide.runCooldownLeft(player) > 0) {
                if (flute > 0) cool(player, flute);
                playTune(player, 0.5f);
                player.sendSystemMessage(Component.translatable("hyperspace_wormholes.flute.unanswered").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            Species legend = PokemonSpecies.getByName(species);
            if (legend == null) return InteractionResultHolder.fail(stack);
            if (UltraWarpRules.inHyperspace(player)) {
                UltraWarpRules.tellFailed(player, legend.getTranslatedName());
                return InteractionResultHolder.fail(stack);
            }

            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.setAttached(Wormholes.NEXT_FLUTE, level.getGameTime() + LEGEND_FLUTE_COOLDOWN_TICKS);
            cool(player, LEGEND_FLUTE_COOLDOWN_TICKS);
            playTune(player, species.equals("lunala") ? 0.7f : 1.05f);
            Pokemon called = legend.create(70);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvent.createVariableRangeEvent(LegendRoar.cryId(called)), SoundSource.NEUTRAL, 3f, 1f);
            player.sendSystemMessage(Component.translatable("hyperspace_wormholes.flute.called",
                    legend.getTranslatedName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (player.getVehicle() != null) player.stopRiding();
            UltraWarpRide.startSummoned(player, called);
            return InteractionResultHolder.consume(stack);
        }
    }
}
