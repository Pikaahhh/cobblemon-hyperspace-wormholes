package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The Ultra Deep Sea: no drowning, walking on the sea floor with lighter gravity, nothing hostile, and dead coral.
 */
public final class UltraDeepSea {
    private static final double GRAVITY = 0.055;
    private static final ResourceLocation LIGHT_GRAVITY = Wormholes.id("deep_sea_gravity");
    private static final ResourceLocation DRY_MINING = Wormholes.id("deep_sea_mining");

    private UltraDeepSea() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltraDeepSea::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Enemy && !entity.hasCustomName() && isDeepSea(level)) entity.discard();
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (isDeepSea(level)) killCoral(chunk);
        });
    }

    private static void killCoral(LevelChunk chunk) {
        boolean changed = false;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(s -> dead(s) != s)) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        BlockState dead = dead(state);
                        if (dead != state) {
                            section.setBlockState(x, y, z, dead, false);
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) chunk.setUnsaved(true);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean below = isDeepSea(player.level());
            modify(player, Attributes.GRAVITY, LIGHT_GRAVITY, below, GRAVITY - 0.08);
            modify(player, Attributes.SUBMERGED_MINING_SPEED, DRY_MINING, below, 0.8);
        }
        ServerLevel sea = server.getLevel(Wormholes.ULTRA_DEEP_SEA);
        if (sea == null) return;
        for (ServerPlayer player : sea.players()) {
            breathe(player);
            if (player.getVehicle() instanceof LivingEntity living) breathe(living);
        }
    }

    private static boolean isDeepSea(Level level) {
        return level.dimension() == Wormholes.ULTRA_DEEP_SEA;
    }

    private static BlockState dead(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft") || !id.getPath().contains("coral") || id.getPath().startsWith("dead_")) return state;
        Block dead = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("dead_" + id.getPath()));
        return dead == Blocks.AIR ? state : dead.withPropertiesOf(state);
    }

    private static void modify(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, boolean on, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance.hasModifier(id) == on) return;
        if (on) instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        else instance.removeModifier(id);
    }

    private static void breathe(LivingEntity entity) {
        if (entity.getAirSupply() < entity.getMaxAirSupply()) entity.setAirSupply(entity.getMaxAirSupply());
    }
}
