package dev.hyperspacewormholes.wormholes.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button for this mod; only loaded when Mod Menu is installed. */
public final class GameplayConfigMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GameplayConfigScreen::new;
    }
}
