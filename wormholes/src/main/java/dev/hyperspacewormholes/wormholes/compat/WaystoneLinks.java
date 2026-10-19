package dev.hyperspacewormholes.wormholes.compat;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional Waystones support. A legend holding a Warp Stone flies you through wormholes that lead to your activated
 * waystones. All done with reflection.
 */
public final class WaystoneLinks {
    private static final String API = "net.blay09.mods.waystones.api.WaystonesAPI";
    private static final TagKey<Item> WARP_STONES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("waystones", "warp_stones"));

    public record Target(UUID id, String name, ResourceKey<Level> dimension, BlockPos pos) {}

    public record Landing(ServerLevel level, Vec3 pos, float yaw) {}

    public static boolean isWarpStone(ItemStack stack) {
        return stack.is(WARP_STONES);
    }

    private static boolean available() {
        return FabricLoader.getInstance().isModLoaded("waystones");
    }

    @SuppressWarnings("unchecked")
    public static List<Target> activated(ServerPlayer player) {
        if (!available()) return List.of();
        try {
            Collection<Object> waystones = (Collection<Object>) staticCall("getActivatedWaystones", player);
            List<Target> targets = new ArrayList<>();
            for (Object w : waystones) {
                if (!(Boolean) call(w, "isValid")) continue;
                targets.add(new Target((UUID) call(w, "getWaystoneUid"), ((Component) call(w, "getEffectiveName")).getString(),
                        (ResourceKey<Level>) call(w, "getDimension"), (BlockPos) call(w, "getPos")));
            }
            return targets;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.warn("Couldn't read waystones for {}", player.getName().getString(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static Optional<Landing> landing(MinecraftServer server, Target target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (!available() || level == null) return Optional.empty();
        try {
            Optional<Object> waystone = (Optional<Object>) staticCall("getWaystone", server, target.id());
            if (waystone.isEmpty()) return Optional.empty();
            Optional<Object> destination = (Optional<Object>) call(waystone.get(), "resolveDestination", level);
            if (destination.isEmpty()) return Optional.empty();
            return Optional.of(new Landing(level, (Vec3) call(destination.get(), "location"),
                    ((Direction) call(destination.get(), "direction")).toYRot()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.warn("Couldn't resolve waystone {}", target.name(), e);
            return Optional.empty();
        }
    }

    private static Object staticCall(String name, Object... args) throws ReflectiveOperationException {
        return find(Class.forName(API), name, args).invoke(null, args);
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = find(target.getClass(), name, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method find(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] params = m.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < args.length && fits; i++) fits = params[i].isInstance(args[i]);
            if (fits) return m;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
