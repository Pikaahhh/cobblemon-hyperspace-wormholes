package dev.hyperspacewormholes.hyperspace.mixin;

import com.metacontent.cobblenav.spawndata.CheckedSpawnData;
import com.metacontent.cobblenav.utils.WeightedBucket;
import dev.hyperspacewormholes.hyperspace.CobbleNavSpawns;
import kotlin.Pair;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Shows the Hyperspace spawn pool in CobbleNav while you're in Hyperspace. */
@Pseudo
@Mixin(targets = "com.metacontent.cobblenav.spawndata.SpawnDataHelper", remap = false)
public class SpawnDataHelperMixin {
    @Inject(method = "checkPlayerSpawns", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hyperspaceSpawns(ServerPlayer player, String bucketName,
                                           CallbackInfoReturnable<Pair<WeightedBucket, List<CheckedSpawnData>>> cir) {
        Pair<WeightedBucket, List<CheckedSpawnData>> result = CobbleNavSpawns.check(player, bucketName);
        if (result != null) cir.setReturnValue(result);
    }
}
