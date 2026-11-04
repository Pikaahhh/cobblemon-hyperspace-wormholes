package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feed a donut to Hoopa (or anything in the can_feed_donut tag) at a spatial distortion and, like in Legends Z-A, its
 * power turns the distortion into a portal to Hyperspace. Right-click Hoopa with the donut, or right-click the
 * distortion while a Hoopa is in your party; Hoopa plays the DLC's scene (HoopaFeed) and the portal opens at its throw,
 * but a fainted Hoopa or one in a battle opens it straight away. Space at the distortion is checked before Hoopa eats,
 * so a refused donut is kept; away from a distortion Hoopa won't eat it and you're pointed to the nearest one.
 */
public class Feeding {
    private static Set<ResourceLocation> feeders = Set.of();
    private static final Map<UUID, Integer> lastClick = new HashMap<>();

    public static void register() {
        HoopaFeed.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> feeders = load(server.getResourceManager()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> feeders = load(server.getResourceManager()));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DonutItem) || !isHoopa(entity)) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            if (clickedTwice(player) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            feedAt(serverPlayer, stack, (PokemonEntity) entity, null);
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world.isClientSide() || !(stack.getItem() instanceof DonutItem) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            if (clickedTwice(player)) return InteractionResultHolder.success(stack);
            if (Distortions.near(serverPlayer) == null) {
                Distortions.noneNearby(serverPlayer);
                return InteractionResultHolder.success(stack);
            }
            PokemonEntity hoopa = hoopaOut(serverPlayer);
            Pokemon party = hoopa == null ? partyHoopa(serverPlayer) : null;
            if (hoopa == null && party == null) {
                serverPlayer.displayClientMessage(Component.translatable("distortion.hyperspace_wormholes.no_hoopa").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            feedAt(serverPlayer, stack, hoopa, party);
            return InteractionResultHolder.success(stack);
        });
    }

    private static boolean clickedTwice(Player player) {
        int tick = player.getServer().getTickCount();
        Integer last = lastClick.put(player.getUUID(), tick);
        return last != null && tick - last <= 5;
    }

    private static void feedAt(ServerPlayer player, ItemStack stack, PokemonEntity out, Pokemon party) {
        Distortions.Distortion here = Distortions.near(player);
        if (here == null) {
            Distortions.noneNearby(player);
            return;
        }
        if (HoopaFeed.busy(player, here, out)) return;
        boolean shown = out != null ? !out.isBattling() && !out.isBusy()
                : !party.isFainted() && BattleRegistry.getBattleByParticipatingPlayer(player) == null;
        if (!shown) {
            if (open(player, stack)) feed(player.serverLevel(), out != null ? out.position() : player.position());
            return;
        }
        if (!Trips.ready(player.server)) return;
        if (!Portals.grounded(player.serverLevel(), here.base())) {
            refuse(player, "ground");
            return;
        }
        if (!Portals.fits(player.serverLevel(), here.base(), here.normal())) {
            refuse(player, "room");
            return;
        }
        ItemStack donut = stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        HoopaFeed.start(player, here, donut, out, party);
    }

    private static void refuse(ServerPlayer player, String why) {
        player.sendSystemMessage(Component.translatable("distortion.hyperspace_wormholes." + why).withStyle(ChatFormatting.RED));
    }

    private static boolean open(ServerPlayer player, ItemStack stack) {
        Distortions.Distortion here = Distortions.near(player);
        if (here == null) {
            Distortions.noneNearby(player);
            return false;
        }
        if (!Distortions.open(player, here, stack)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }

    private static void feed(ServerLevel level, Vec3 at) {
        double x = at.x, y = at.y, z = at.z;
        level.sendParticles(ParticleTypes.HEART, x, y + 1, z, 6, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 30, 0.3, 0.3, 0.3, 0.5);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1f, 1.2f);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1f);
    }

    private static boolean isHoopa(Entity entity) {
        return entity instanceof PokemonEntity pokemon && feeders.contains(pokemon.getPokemon().getSpecies().getResourceIdentifier());
    }

    private static Pokemon partyHoopa(ServerPlayer player) {
        Pokemon fainted = null;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (!feeders.contains(pokemon.getSpecies().getResourceIdentifier())) continue;
            if (!pokemon.isFainted()) return pokemon;
            if (fainted == null) fainted = pokemon;
        }
        return fainted;
    }

    private static PokemonEntity hoopaOut(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null && !entity.isRemoved() && entity.level() == player.level() && isHoopa(entity)
                    && entity.distanceToSqr(player) < 32 * 32) {
                return entity;
            }
        }
        return null;
    }

    private static Set<ResourceLocation> load(ResourceManager resources) {
        Set<ResourceLocation> found = new HashSet<>();
        for (String name : Spawns.readTag(resources, "tags/pokemon/can_feed_donut.json")) {
            found.add(name.contains(":") ? ResourceLocation.parse(name) : ResourceLocation.fromNamespaceAndPath("cobblemon", name));
        }
        return found;
    }
}
