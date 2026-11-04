package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A placement filter (hyperspace_wormholes:off_zone) that drops any spot standing on or inside the nearest copy of a
 * zone, i.e. where the zone has a block at or under it in that column. The Ultra Jungle's trees, bushes, grass and
 * melons read heightmaps that include the spawn structure and would otherwise grow all over the islet. Spots on the
 * ground under the vine ropes still count.
 */
public class OffZoneFilter extends PlacementFilter {
    public static final MapCodec<OffZoneFilter> CODEC = Codec.STRING.fieldOf("zone").xmap(OffZoneFilter::new, f -> f.zone);
    public static final PlacementModifierType<OffZoneFilter> TYPE = () -> CODEC;
    private final String zone;

    public OffZoneFilter(String zone) {
        this.zone = zone;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return !Zone.get(zone).near(pos.getX(), pos.getZ()).holdsAtOrBelow(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
