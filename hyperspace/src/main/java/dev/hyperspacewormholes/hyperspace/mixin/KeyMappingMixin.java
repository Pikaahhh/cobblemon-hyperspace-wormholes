package dev.hyperspacewormholes.hyperspace.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hyperspacewormholes.hyperspace.MoveKeys;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 1.21.1 keeps one key mapping per key, so the quick battle move keys (1 to 4 by default, the same as hotbar slots 1 to
 * 4) would take those keys from the hotbar. After the key table is rebuilt, any key a move key shares goes back to the
 * other mapping on it; the move keys are read straight off the keyboard (QuickBattleTargeting.pressed), so they still
 * work.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void hyperspace_wormholes$shareMoveKeys(CallbackInfo ci) {
        for (KeyMapping move : MoveKeys.keys) {
            InputConstants.Key key = ((KeyMappingKeyAccessor) move).hyperspace_wormholes$key();
            if (MAP.get(key) != move) continue;
            KeyMapping other = null;
            for (KeyMapping mapping : ALL.values()) {
                if (mapping != move && !MoveKeys.is(mapping) && mapping.same(move)) other = mapping;
            }
            if (other != null) MAP.put(key, other);
        }
    }
}
