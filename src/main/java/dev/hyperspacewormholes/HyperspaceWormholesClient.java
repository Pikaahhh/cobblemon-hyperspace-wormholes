package dev.hyperspacewormholes;

import dev.hyperspacewormholes.hyperspace.HyperspaceClient;
import dev.hyperspacewormholes.usum.client.MoveFxClient;
import dev.hyperspacewormholes.wormholes.client.GameplayConfigScreen;
import dev.hyperspacewormholes.wormholes.client.WormholesClient;
import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCameraClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

final class HyperspaceWormholesClient {
    private HyperspaceWormholesClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        HyperspaceClient.init(modBus);
        WormholesClient.init(modBus);
        MoveFxClient.init(modBus);
        BattleCameraClient.init(modBus);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new GameplayConfigScreen(parent));
    }
}
