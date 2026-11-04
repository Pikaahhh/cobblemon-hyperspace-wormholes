package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What the Necrozma cutscenes share: the easing ramps their light and screen fades are timed with, the screen fills,
 * the camera's block checks (pulling a blocked camera in toward what it frames) and the cutscene motion held on the
 * scene's frame as a Cobblemon primary animation.
 */
final class Fx {
    static final String MOTIONS = "necrozma_ultraburst";
    private static final double[] BLOCK_CHECKS = {-0.2, 0, 0.2};

    private Fx() {}

    static float ramp(double f, double from, double to) {
        return (float) Mth.clamp((f - from) / (to - from), 0, 1);
    }

    static float smooth(double f, double from, double to) {
        float t = ramp(f, from, to);
        return t * t * (3 - 2 * t);
    }

    static float window(double f, double in, double full, double out, double gone) {
        return smooth(f, in, full) * (1 - smooth(f, out, gone));
    }

    static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void tint(GuiGraphics g, int w, int h, float alpha, int rgb) {
        if (alpha > 0.002f) g.fill(0, 0, w, h, argb(alpha, rgb));
    }

    static void fadeBack(GuiGraphics g, int fadeAge, float partialTick, int ticks) {
        float black = 1 - Mth.clamp((fadeAge + partialTick) / ticks, 0, 1);
        if (black > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb(black, 0x000000));
    }

    static boolean blocked(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        for (double dy : BLOCK_CHECKS) {
            BlockPos block = BlockPos.containing(pos.x, pos.y + dy, pos.z);
            if (!mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty()) return true;
        }
        return false;
    }

    static boolean sees(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.clip(new ClipContext(to, from, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    /** How much of the way from subject to pos the camera can keep (in 0.05 steps, down to min) and be clear. */
    static double keep(Vec3 pos, Vec3 subject, double min) {
        double keep = 1;
        while (keep > min) {
            Vec3 at = subject.add(pos.subtract(subject).scale(keep));
            if (!blocked(at) && sees(at, subject)) break;
            keep -= 0.05;
        }
        return Math.max(keep, min);
    }

    /** A cutscene motion (animation file MOTIONS) played as a primary animation held on the scene's clock. */
    static final class Motion {
        private @Nullable PrimaryAnimation primary;
        private @Nullable BedrockActiveAnimation active;
        private @Nullable String name;

        boolean playing(PosableState state, String name) {
            return state.getPrimaryAnimation() == primary && name.equals(this.name) && primary != null && active != null;
        }

        float length() {
            return (float) active.getAnimation().getAnimationLength();
        }

        void hold(float started) {
            if (Math.abs(active.getStartedSeconds() - started) > 1e-3f) {
                active.setStartedSeconds(started);
                primary.setStarted(started);
            }
        }

        static @Nullable BedrockAnimation find(String name) {
            return BedrockAnimationRepository.INSTANCE.getAnimationOrNull(MOTIONS, "animation." + MOTIONS + "." + name);
        }

        void start(PosableState state, BedrockAnimation animation, String name, float started) {
            BedrockActiveAnimation active = new BedrockActiveAnimation(animation);
            PrimaryAnimation primary = new PrimaryAnimation(active, t -> 1f, Set.of(), false);
            state.addPrimaryAnimation(primary);
            active.setStartedSeconds(started);
            primary.setStarted(started);
            this.primary = primary;
            this.active = active;
            this.name = name;
        }

        void stop(int entityId) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(entityId) instanceof PokemonEntity necrozma
                    && necrozma.getDelegate() instanceof PosableState state && primary != null && state.getPrimaryAnimation() == primary) {
                state.setPrimaryAnimation(null);
            }
            primary = null;
            active = null;
            name = null;
        }
    }
}
