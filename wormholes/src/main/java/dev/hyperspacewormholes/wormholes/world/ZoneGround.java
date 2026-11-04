package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * A density function (hyperspace_wormholes:zone_ground) giving a ground height in blocks: its argument, the world's own
 * height, blended into the zone's ground file round every copy of the zone (Zone.ground), so a world's noise rises
 * into the game's own terrain round its zone and runs under the zone's sand (the Ultra Desert: its sand pyramids and
 * the dunes either side of the trough, then the world's dunes further out).
 */
public record ZoneGround(String zone, DensityFunction argument) implements DensityFunction {
    public static final MapCodec<ZoneGround> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("zone").forGetter(ZoneGround::zone),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(ZoneGround::argument)).apply(i, ZoneGround::new));

    @Override
    public double compute(FunctionContext context) {
        return Zone.get(zone).ground(context.blockX(), context.blockZ(), argument.compute(context));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ZoneGround(zone, argument.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return Math.min(argument.minValue(), -64);
    }

    @Override
    public double maxValue() {
        return Math.max(argument.maxValue(), 320);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }
}
