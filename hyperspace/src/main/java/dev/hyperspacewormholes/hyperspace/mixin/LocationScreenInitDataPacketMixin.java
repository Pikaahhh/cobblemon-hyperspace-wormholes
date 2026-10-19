package dev.hyperspacewormholes.hyperspace.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts CobbleNav's spawn buckets as common, uncommon, rare, ultra-rare, boss.
 */
@Pseudo
@Mixin(targets = "com.metacontent.cobblenav.networking.packet.client.LocationScreenInitDataPacket", remap = false)
public class LocationScreenInitDataPacketMixin {
    private static final List<String> ORDER = List.of("common", "uncommon", "rare", "ultra-rare", "boss");

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<String> hyperspace_wormholes$orderBuckets(List<String> buckets) {
        List<String> sorted = new ArrayList<>(buckets);
        sorted.sort(Comparator.comparingInt(bucket -> ORDER.contains(bucket) ? ORDER.indexOf(bucket) : ORDER.size()));
        return sorted;
    }
}
