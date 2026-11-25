package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Rules for our Ultra dimensions. They get wiped every world load, anyone who logged out in one gets sent home when
 * they come back, and no waystones in there (and no building, UltraNoBuilding).
 */
public final class UltraDimensions {
    private static final String WAYSTONES = "waystones";
    private static final int SEND_OUT_DELAY = 10;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private UltraDimensions() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> wipe(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (isUltra(event.getEntity().level())) JOINED.put(event.getEntity().getUUID(), SEND_OUT_DELAY);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            Player player = event.getEntity();
            Level level = event.getLevel();
            if (blocked(player, level, player.getItemInHand(event.getHand()))
                    || BuiltInRegistries.BLOCK.getKey(level.getBlockState(event.getPos()).getBlock()).getNamespace().equals(WAYSTONES)
                    && isUltra(level) && refuse(player, level)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (blocked(event.getEntity(), event.getLevel(), event.getItemStack())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
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
        HomePoint home = player.getExistingDataOrNull(Wormholes.RETURN_POINT);
        player.removeData(Wormholes.RETURN_POINT);
        ServerLevel level = home == null ? null : player.server.getLevel(home.dimension());
        if (level == null) {
            home = HomePoint.worldSpawn(player.server);
            level = player.server.overworld();
        }
        Vec3 at = PassengerLandings.around(level, home.pos(), home.yRot(), 1, List.of(player), null, false).getFirst();
        player.teleportTo(level, at.x, at.y, at.z, home.yRot(), 0f);
    }
}
