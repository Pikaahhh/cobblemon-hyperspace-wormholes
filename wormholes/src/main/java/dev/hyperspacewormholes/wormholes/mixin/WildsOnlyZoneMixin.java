package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.world.ZoneRocksFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No feature but Crag World's rocks (ZoneRocksFeature) generates in the Ultra Space Wilds, the Ultra Deep Sea and the
 * Ultra Megalopolis (UltraZones.ZONE_ONLY), beside the zone's own structure (ZoneStructure): any feature that ends up
 * in their biomes, such as Cobblemon's medicinal leeks in Water World or whatever another mod adds to many biomes,
 * is skipped there.
 */
@Mixin(PlacedFeature.class)
public class WildsOnlyZoneMixin {
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$onlyZone(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                                               BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (UltraZones.ZONE_ONLY.contains(level.getLevel().dimension())
                && !(((PlacedFeature) (Object) this).feature().value().feature() instanceof ZoneRocksFeature)) {
            cir.setReturnValue(false);
        }
    }
}
