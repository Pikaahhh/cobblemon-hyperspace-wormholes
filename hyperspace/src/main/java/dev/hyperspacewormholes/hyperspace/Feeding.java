package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feed a donut to Hoopa (or anything in the can_feed_donut tag) and you get a Hoopa Ring with that donut's boosts.
 */
public class Feeding {
    private static Set<ResourceLocation> feeders = Set.of();
    private static final Map<UUID, Integer> lastClick = new HashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> feeders = load(server.getResourceManager()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> feeders = load(server.getResourceManager()));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DonutItem) || !isHoopa(entity)) {
                return InteractionResult.PASS;
            }
            if (world.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            int tick = world.getServer().getTickCount();
            Integer last = lastClick.put(player.getUUID(), tick);
            if (last != null && tick - last <= 5) {
                return InteractionResult.SUCCESS;
            }
            ItemStack ring = new ItemStack(ModItems.HOOPA_RING);
            ring.set(CobblemonItemComponents.INGREDIENT, stack.get(CobblemonItemComponents.INGREDIENT));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            feed((ServerLevel) world, entity, ring);
            return InteractionResult.SUCCESS;
        });
    }

    private static void feed(ServerLevel level, Entity hoopa, ItemStack ring) {
        double x = hoopa.getX(), y = hoopa.getY(), z = hoopa.getZ();
        ItemEntity drop = new ItemEntity(level, x, y + 0.5, z, ring);
        drop.setPickUpDelay(10);
        level.addFreshEntity(drop);
        level.sendParticles(ParticleTypes.HEART, x, y + 1, z, 6, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 30, 0.3, 0.3, 0.3, 0.5);
        level.playSound(null, hoopa.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1f, 1.2f);
        level.playSound(null, hoopa.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1f);
    }

    private static boolean isHoopa(Entity entity) {
        return entity instanceof PokemonEntity pokemon && feeders.contains(pokemon.getPokemon().getSpecies().getResourceIdentifier());
    }

    private static Set<ResourceLocation> load(ResourceManager resources) {
        Set<ResourceLocation> found = new HashSet<>();
        for (String name : Spawns.readTag(resources, "tags/pokemon/can_feed_donut.json")) {
            found.add(name.contains(":") ? ResourceLocation.parse(name) : ResourceLocation.fromNamespaceAndPath("cobblemon", name));
        }
        return found;
    }
}
