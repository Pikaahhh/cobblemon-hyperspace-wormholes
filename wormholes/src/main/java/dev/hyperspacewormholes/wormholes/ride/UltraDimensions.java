package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Rules for our Ultra dimensions. They get wiped every world load, anyone who logged out in one gets sent home when
 * they come back, and no waystones in there.
 */
public final class UltraDimensions {
    private static final String WAYSTONES = "waystones";
    private static final int SEND_OUT_DELAY = 10;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private UltraDimensions() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(UltraDimensions::wipe);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (isUltra(handler.getPlayer().level())) JOINED.put(handler.getPlayer().getUUID(), SEND_OUT_DELAY);
        });
        ServerTickEvents.END_SERVER_TICK.register(UltraDimensions::tick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                blocked(player, level, player.getItemInHand(hand))
                        || BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).getNamespace().equals(WAYSTONES)
                        && isUltra(level) && refuse(player, level)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> blocked(player, level, player.getItemInHand(hand))
                ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
    }

    public static boolean isUltra(Level level) {
        return level.dimension().location().getNamespace().equals(Wormholes.MOD_ID);
    }

    private static boolean blocked(Player player, Level level, ItemStack stack) {
        return !stack.isEmpty() && isUltra(level)
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(WAYSTONES) && refuse(player, level);
    }

    private static boolean refuse(Player player, Level level) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            serverPlayer.sendSystemMessage(Component.literal("A disturbance prevents you from using waystones inside ")
                    .append(UltraWarpRide.dimensionName(serverLevel)).append(".").withStyle(ChatFormatting.RED));
        }
        return true;
    }

    private static void wipe(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(Wormholes.MOD_ID);
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        } catch (IOException e) {
            Wormholes.LOGGER.error("Couldn't wipe the Ultra dimensions at {}", folder, e);
        }
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Integer>> it = JOINED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) sendOut(player);
        }
    }

    private static void sendOut(ServerPlayer player) {
        if (!isUltra(player.level())) return;
        HomePoint home = player.getAttached(Wormholes.RETURN_POINT);
        player.removeAttached(Wormholes.RETURN_POINT);
        ServerLevel level = home == null ? null : player.server.getLevel(home.dimension());
        if (level == null) {
            home = HomePoint.worldSpawn(player.server);
            level = player.server.overworld();
        }
        player.teleportTo(level, home.pos().x, home.pos().y, home.pos().z, home.yRot(), 0f);
    }
}
