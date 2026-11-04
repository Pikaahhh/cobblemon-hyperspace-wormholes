package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.UltraWorldMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.tint;

import java.util.HashSet;
import java.util.Set;

/**
 * What the client adds on and round Ultra Necrozma in its defeat scene (NecrozmaSceneCinematic DEFEAT), timed to Ultra Sun's field sequence 28: its black bits fading in (DIM aspects swap in cutscene-only textures built by MsdDerivedPack), landing dust with a camera shake, the scene's music and sound effects, and a fade from and to black.
 * The change back to base Necrozma is Mega Showdown's mega evolution effect, run by the server from NecrozmaSceneCinematic.MEGA_FROM_TICK (datapack effect hyperspace_wormholes:necrozma_power_down, built by MsdDerivedPack).
 * Its particles are recoloured from gold to dark grey and sized for Ultra Necrozma's hitbox, so the ball keeps its size when the form changes inside it.
 */
final class DefeatEffects {
    static final String DIM = "ultra-dim-";
    private static final int[] DIM_AT = {92, 106, 120};
    private static final int LAND = 86;
    private static int dim;
    private static boolean landed;
    private static float size = 1;
    private static @Nullable SoundInstance stream;

    private DefeatEffects() {}

    static void start(PokemonEntity necrozma, float sizeFactor) {
        size = sizeFactor;
        dim = 0;
        landed = false;
        setDim(necrozma, 0);
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        if (stream != null) mc.getSoundManager().stop(stream);
        stream = new SimpleSoundInstance(Wormholes.id("usum.necrozma_defeat"), SoundSource.MASTER, 1f, 1f,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(stream);
        UltraWorldMusic.holdReactiveMusic(stream);
    }

    static void tick(double f, @Nullable PokemonEntity necrozma) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (necrozma != null && !NecrozmaSceneCinematic.ultraGone()) {
            int want = 0;
            for (int at : DIM_AT) if (f >= at) want++;
            if (want != dim) setDim(necrozma, want);
        }
        if (!landed && f >= LAND) {
            landed = true;
            RandomSource random = mc.level.getRandom();
            Vec3 c = NecrozmaSceneCinematic.base();
            for (int i = 0; i < 70; i++) {
                double a = random.nextDouble() * Mth.TWO_PI, r = (1 + random.nextDouble() * 5) * size;
                mc.level.addParticle(i % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + 0.3,
                        c.z + Math.sin(a) * r, Math.cos(a) * 0.12, 0.03 + random.nextDouble() * 0.05, Math.sin(a) * 0.12);
            }
        }
    }

    static void formChanged() {
        dim = 0;
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (stream != null) mc.getSoundManager().stop(stream);
        stream = null;
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PokemonEntity p && p.getEntityData().get(PokemonEntity.getASPECTS()).stream().anyMatch(a -> a.startsWith(DIM))) {
                setDim(p, 0);
            }
        }
    }

    private static void setDim(PokemonEntity necrozma, int stage) {
        dim = stage;
        Set<String> aspects = necrozma.getEntityData().get(PokemonEntity.getASPECTS());
        Set<String> next = new HashSet<>();
        for (String a : aspects) if (!a.startsWith(DIM)) next.add(a);
        if (stage > 0 && next.contains("ultra-fusion")) next.add(DIM + stage);
        if (!next.equals(aspects)) necrozma.getEntityData().set(PokemonEntity.getASPECTS(), next);
    }

    static Vec3 shake(double f) {
        if (f < LAND || f > LAND + 16) return Vec3.ZERO;
        double amp = 0.14 * size * (1 - (f - LAND) / 16);
        return new Vec3(Math.sin(f * 2.3) * amp, Math.cos(f * 3.1) * amp, Math.sin(f * 1.7 + 2) * amp);
    }

    static void screen(GuiGraphics g, double f, int w, int h) {
        tint(g, w, h, 1 - (float) Mth.clamp(f / 8, 0, 1), 0x000000);
        tint(g, w, h, (float) Mth.clamp((f - (NecrozmaSceneCinematic.DEFEAT_END - 10)) / 10, 0, 1), 0x000000);
    }
}
