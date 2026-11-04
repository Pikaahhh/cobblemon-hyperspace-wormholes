package dev.hyperspacewormholes.wormholes.client;

import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Packets that reach the client before the entity they're for (its data, its riders, its removal), kept for KEEP_MILLIS
 * and applied when it shows up. Cobblemon spawns a Pokemon with its own packet, which Fabric runs a moment after the
 * packets sent with it. A kept removal never applies to a player: when you change worlds the server tells you to drop
 * everyone from the old one only after your client has moved into the new one, which would remove a friend arriving
 * through the same wormhole.
 */
public final class LateSpawns {
    private static final long KEEP_MILLIS = 5000;
    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        final long at = Util.getMillis();
        final List<Consumer<Entity>> apply = new ArrayList<>();
        boolean removed;
    }

    private LateSpawns() {}

    public static void later(int id, Consumer<Entity> apply) {
        pending(id).apply.add(apply);
    }

    public static void removed(int id) {
        pending(id).removed = true;
    }

    public static void added(ClientLevel level, Entity entity) {
        Pending pending = PENDING.remove(entity.getId());
        if (pending == null || Util.getMillis() - pending.at > KEEP_MILLIS) return;
        boolean player = entity instanceof Player;
        if (pending.removed && !player) level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        else pending.apply.forEach(apply -> apply.accept(entity));
    }

    private static Pending pending(int id) {
        long now = Util.getMillis();
        PENDING.values().removeIf(p -> now - p.at > KEEP_MILLIS);
        return PENDING.computeIfAbsent(id, i -> new Pending());
    }
}
