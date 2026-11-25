package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.settings.ServerSettings;
import dev.hyperspacewormholes.hyperspace.LevelScaling;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon sends the party on join before its server settings, which carry the level cap Hyperspace raises to
 * LevelScaling.maxLevel. A client that just started still checks party levels against its own cap of 100 then, so
 * rejoining in Hyperspace with boosted Pokemon failed to decode. The client's own cap is raised too, or the boosted
 * levels it is sent are dropped. NeoForge's join event comes too late, the party can already be decoding by then.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void hyperspace_wormholes$raiseLevelCap(CallbackInfo ci) {
        if (ServerSettings.INSTANCE.getMaxPokemonLevel() < LevelScaling.maxLevel) ServerSettings.INSTANCE.setMaxPokemonLevel(LevelScaling.maxLevel);
        if (Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel() < LevelScaling.maxLevel) Cobblemon.INSTANCE.getConfig().setMaxPokemonLevel(LevelScaling.maxLevel);
    }
}
