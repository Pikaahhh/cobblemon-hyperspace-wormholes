package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Rules about the Ultra Warp dimension and Hyperspace shared by both sides and the mixins. */
public final class UltraWarpRules {
    private static final ResourceLocation HYPERSPACE = ResourceLocation.fromNamespaceAndPath("hyperspace_wormholes", "hyperspace");
    private static final Set<Entity> HELD = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private UltraWarpRules() {}

    public static boolean inWarp(Entity entity) {
        return entity.level().dimension() == Wormholes.ULTRA_WARP;
    }

    public static boolean inHyperspace(Entity entity) {
        return entity.level().dimension().location().equals(HYPERSPACE);
    }

    public static void tellFailed(ServerPlayer player, Component name) {
        player.sendSystemMessage(Component.translatable("hyperspace_wormholes.msg.wormhole_failed", name).withStyle(ChatFormatting.RED));
    }

    public static boolean isUnderwater(Level level) {
        return level.dimension() == Wormholes.ULTRA_DEEP_SEA;
    }

    public static boolean isUnderwater(Entity entity) {
        return isUnderwater(entity.level());
    }

    public static void hold(Entity entity) {
        HELD.add(entity);
    }

    public static void release(Entity entity) {
        HELD.remove(entity);
    }

    public static boolean isPinned(Entity entity) {
        return inWarp(entity) || HELD.contains(entity);
    }

    public static boolean isViewLocked(Entity entity) {
        return entity instanceof Player && isPinned(entity);
    }

    public static boolean keepsMounted(Entity entity) {
        return isPinned(entity);
    }
}
