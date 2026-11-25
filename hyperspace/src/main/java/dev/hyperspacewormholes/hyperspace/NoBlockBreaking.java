package dev.hyperspacewormholes.hyperspace;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class NoBlockBreaking {
    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (isHyperspace(event.getLevel())) event.setCanceled(true);
        });

        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof Level level && isHyperspace(level)) event.setCanceled(true);
        });
    }

    public static boolean isHyperspace(Level level) {
        return level.dimension().equals(Hyperspace.HYPERSPACE);
    }
}
