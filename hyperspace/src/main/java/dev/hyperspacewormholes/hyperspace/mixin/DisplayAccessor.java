package dev.hyperspacewormholes.hyperspace.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to display entities' billboard, transform, brightness, view range and interpolation setters, for the portal
 * and ring displays and the spatial distortions' light column and label.
 */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setBillboardConstraints")
    void hyperspace_wormholes$setBillboard(Display.BillboardConstraints constraints);

    @Invoker("setTransformation")
    void hyperspace_wormholes$setTransformation(Transformation transformation);

    @Invoker("setBrightnessOverride")
    void hyperspace_wormholes$setBrightness(Brightness brightness);

    @Invoker("setTransformationInterpolationDuration")
    void hyperspace_wormholes$setDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void hyperspace_wormholes$setDelay(int ticks);

    @Invoker("setViewRange")
    void hyperspace_wormholes$setViewRange(float range);
}
