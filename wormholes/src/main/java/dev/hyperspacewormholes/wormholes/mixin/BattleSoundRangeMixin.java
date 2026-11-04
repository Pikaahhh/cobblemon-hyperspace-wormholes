package dev.hyperspacewormholes.wormholes.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends Cobblemon's own battle sounds (its move.*, impact.*, status.* and pokemon.*.cry events) to every player
 * within HEARING blocks instead of vanilla's 16, matching the hearing range our assets/cobblemon/sounds.json gives
 * them. The server picks who gets a sound by SoundEvent.getRange, so a player further than 16 blocks from a Pokemon
 * in battle wouldn't get its move sounds at all, however far the client could hear them.
 */
@Mixin(SoundEvent.class)
public abstract class BattleSoundRangeMixin {
    private static final float HEARING = 48;

    @Inject(method = "getRange", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$battleSoundRange(float volume, CallbackInfoReturnable<Float> cir) {
        ResourceLocation id = ((SoundEvent) (Object) this).getLocation();
        if (!id.getNamespace().equals("cobblemon")) return;
        String path = id.getPath();
        if (path.startsWith("move.") || path.startsWith("impact.") || path.startsWith("status.")
                || path.startsWith("pokemon.") && path.endsWith(".cry")) {
            cir.setReturnValue(Math.max(cir.getReturnValueF(), HEARING));
        }
    }
}
