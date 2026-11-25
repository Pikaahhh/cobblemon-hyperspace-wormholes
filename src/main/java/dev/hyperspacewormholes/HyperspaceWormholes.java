package dev.hyperspacewormholes;

import dev.hyperspacewormholes.hyperspace.Hyperspace;
import dev.hyperspacewormholes.usum.MoveFx;
import dev.hyperspacewormholes.usum.battle.BattleSpacing;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(HyperspaceWormholes.MOD_ID)
public final class HyperspaceWormholes {
    public static final String MOD_ID = "hyperspace_wormholes";

    public HyperspaceWormholes(IEventBus modBus, ModContainer container, Dist dist) {
        modBus.addListener(Packets::register);
        Hyperspace.init(modBus);
        Wormholes.init(modBus, container);
        MoveFx.init(modBus);
        BattleSpacing.init(modBus);
        // The client halves start once every common half is set up.
        if (dist.isClient()) {
            HyperspaceWormholesClient.init(modBus, container);
        }
    }
}
