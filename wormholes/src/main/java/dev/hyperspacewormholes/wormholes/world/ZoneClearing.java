package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/** A density function (hyperspace_wormholes:zone_clearing) that is 1 within inner blocks of a copy of a zone (every one of the zone's repeat, Zone), easing to 0 by outer, for a world's noise to keep things away from its zone: the Ultra Jungle's noise settings fill in the river craters there and keep the river biome off it. */
public record ZoneClearing(String zone, double inner, double outer) implements DensityFunction.SimpleFunction {
    public static final MapCodec<ZoneClearing> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("zone").forGetter(ZoneClearing::zone),
            Codec.DOUBLE.fieldOf("inner").forGetter(ZoneClearing::inner),
            Codec.DOUBLE.fieldOf("outer").forGetter(ZoneClearing::outer)).apply(i, ZoneClearing::new));

    @Override
    public double compute(FunctionContext context) {
        double d = Zone.get(zone).fromCentre(context.blockX(), context.blockZ());
        double t = Mth.clamp((outer - d) / (outer - inner), 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }
}
