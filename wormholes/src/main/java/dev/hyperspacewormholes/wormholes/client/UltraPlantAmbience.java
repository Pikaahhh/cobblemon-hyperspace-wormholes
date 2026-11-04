package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.ride.PlantXurkitrees;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Things that move in the Ultra Plant besides its lightning: electric sparks round the Xurkitrees still in their statue pose, smoke puffing out of the glowing pockets in the towers' sides, and blue fireflies over the floor.
 * Pockets are found by scanning loaded chunk sections for light blocks; a puff leaves its pocket level, then turns upward once it is past the mouth.
 * Everything goes straight to the particle engine (vanilla's spawning drops particles over 32 blocks away), and a firefly is only drawn inside the view, since vanilla draws every particle and most would lie off screen.
 */
public final class UltraPlantAmbience {
    private static final int SCAN_EVERY = 20, TOWER_TOP = 84;
    private static final int FIREFLY_MAX = 250, FIREFLY_LIFE = 50;
    private static final double FIREFLY_DENSITY = 3e-4;
    private static final float FIREFLY_SIZE = 2.4f, FIREFLY_NEAR = 40;
    private static final float[] FIREFLY_DIM = {0.2f, 0.55f, 1f}, FIREFLY_BRIGHT = {0.4f, 0.85f, 1f};
    private static final ResourceLocation SPECK = ResourceLocation.withDefaultNamespace("generic_0");
    private static final int SPARK_EVERY = 3, SPARK_RANGE = 96;
    private static final int POCKET_CHUNKS = 5, POCKET_EVERY = 4, PUFF_LIMIT = 600;
    private static final int MOUTH_SEARCH = 6;
    private static final double POCKET_IN = 0.16, POCKET_OUT = 0.22, POCKET_BEND = 0.88, POCKET_RISE = 0.07, POCKET_LIFT = 0.006;
    private static final float VIEW_MARGIN = 1.15f;
    private static final double NEAR = 4;

    private record Pocket(BlockPos pos, Direction out, double depth) {}

    private static final class Puff {
        final Particle particle;
        double vx, vy, vz;
        int level;

        Puff(Particle particle, double vx, double vz, int level) {
            this.particle = particle;
            this.vx = vx;
            this.vz = vz;
            this.level = level;
        }
    }

    private static final List<Pocket> pockets = new ArrayList<>();
    private static final List<Puff> puffs = new ArrayList<>();
    private static int ticks;
    private static float viewCos = -1;

    private UltraPlantAmbience() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
        WorldRenderEvents.START.register(context -> {
            Matrix4f p = context.projectionMatrix();
            float tan = VIEW_MARGIN * (float) Math.sqrt(1 / (p.m00() * p.m00()) + 1 / (p.m11() * p.m11()));
            viewCos = (float) (1 / Math.sqrt(1 + tan * tan));
        });
    }

    private static boolean inView(Camera camera, double x, double y, double z) {
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return d < NEAR || dx * look.x() + dy * look.y() + dz * look.z() >= viewCos * d;
    }

    private static void scanPockets(ClientLevel level, BlockPos at) {
        pockets.clear();
        for (int cx = (at.getX() >> 4) - POCKET_CHUNKS; cx <= (at.getX() >> 4) + POCKET_CHUNKS; cx++) {
            for (int cz = (at.getZ() >> 4) - POCKET_CHUNKS; cz <= (at.getZ() >> 4) + POCKET_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Blocks.LIGHT))) continue;
                    int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                if (!section.getBlockState(x, y, z).is(Blocks.LIGHT)) continue;
                                BlockPos pos = new BlockPos((cx << 4) + x, y0 + y, (cz << 4) + z);
                                Direction out = open(level, pos);
                                if (out != null) pockets.add(new Pocket(pos, out, mouth(level, pos, out)));
                            }
                        }
                    }
                }
            }
        }
    }

    private static @Nullable Direction open(ClientLevel level, BlockPos pos) {
        Direction best = null;
        int bestRun = 1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int run = 0;
            while (run < 4 && level.getBlockState(pos.relative(d, run + 1)).isAir()) run++;
            if (run > bestRun) {
                bestRun = run;
                best = d;
            }
        }
        return best;
    }

    private static double mouth(ClientLevel level, BlockPos pos, Direction out) {
        int k = 1;
        for (; k <= MOUTH_SEARCH; k++) {
            BlockPos cell = pos.relative(out, k);
            if (!level.getBlockState(cell).isAir()) break;
            if (level.getBlockState(cell.above()).isAir() && level.getBlockState(cell.above(2)).isAir()) return k + 0.5;
        }
        return k - 0.5;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Wormholes.ULTRA_PLANT) {
            pockets.clear();
            puffs.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        if (ticks % SCAN_EVERY == 0) scanPockets(level, mc.player.blockPosition());
        RandomSource random = level.getRandom();
        fireflies(mc, level, random);
        bend();
        if (ticks % SPARK_EVERY == 0) sparks(mc, level, random);
        if (ticks % POCKET_EVERY == 0) {
            for (Pocket p : pockets) puff(mc, p, random);
        }
    }

    private static void puff(Minecraft mc, Pocket p, RandomSource random) {
        if (puffs.size() >= PUFF_LIMIT) return;
        double ox = p.out().getStepX(), oz = p.out().getStepZ();
        double sx = -oz, sz = ox;
        double side = random.nextGaussian() * 0.25;
        double x = p.pos().getX() + 0.5 + sx * side, z = p.pos().getZ() + 0.5 + sz * side;
        double y = p.pos().getY() + 0.3 + random.nextDouble() * 0.4;
        Particle smoke = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, ox * POCKET_IN, 0, oz * POCKET_IN);
        if (smoke == null) return;
        double speed = POCKET_OUT * (0.8 + random.nextDouble() * 0.4);
        puffs.add(new Puff(smoke, ox * speed, oz * speed, Mth.ceil(p.depth() / POCKET_IN)));
    }

    private static void bend() {
        for (Iterator<Puff> it = puffs.iterator(); it.hasNext(); ) {
            Puff p = it.next();
            if (!p.particle.isAlive()) {
                it.remove();
                continue;
            }
            if (p.level > 0) {
                p.level--;
                double inward = POCKET_IN / Math.hypot(p.vx, p.vz);
                p.particle.setParticleSpeed(p.vx * inward, 0, p.vz * inward);
                continue;
            }
            p.vx *= POCKET_BEND;
            p.vz *= POCKET_BEND;
            p.vy = Math.min(POCKET_RISE, p.vy + POCKET_LIFT);
            p.particle.setParticleSpeed(p.vx, p.vy, p.vz);
        }
    }

    private static void sparks(Minecraft mc, ClientLevel level, RandomSource random) {
        AABB around = mc.player.getBoundingBox().inflate(SPARK_RANGE);
        for (PokemonEntity e : level.getEntitiesOfClass(PokemonEntity.class, around,
                p -> p.getAspects().contains(PlantXurkitrees.STATUE))) {
            double size = e.getBbHeight();
            double spread = 0.12 * size;
            double cy = e.getY() + size * 0.86;
            for (int i = random.nextInt(3); i > 0; i--) {
                mc.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK, e.getX() + random.nextGaussian() * spread,
                        cy + random.nextGaussian() * spread, e.getZ() + random.nextGaussian() * spread,
                        random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
            }
        }
    }

    private static void fireflies(Minecraft mc, ClientLevel level, RandomSource random) {
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        int range = mc.options.getEffectiveRenderDistance() * 16;
        int count = (int) Math.min(FIREFLY_MAX, FIREFLY_DENSITY * 4.0 * range * range);
        TextureAtlasSprite sprite = sprite(mc);
        for (int i = 0; i < count; i++) {
            int x = Mth.floor(mc.player.getX() + (random.nextDouble() * 2 - 1) * range);
            int z = Mth.floor(mc.player.getZ() + (random.nextDouble() * 2 - 1) * range);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            top.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            if (top.getY() >= TOWER_TOP || !floor(level, top)) continue;
            Firefly fly = new Firefly(level, x + random.nextDouble(), top.getY() + 1.2 + random.nextDouble() * 2,
                    z + random.nextDouble(), sprite);
            float t = random.nextFloat();
            fly.setColor(Mth.lerp(t, FIREFLY_DIM[0], FIREFLY_BRIGHT[0]), Mth.lerp(t, FIREFLY_DIM[1], FIREFLY_BRIGHT[1]),
                    Mth.lerp(t, FIREFLY_DIM[2], FIREFLY_BRIGHT[2]));
            fly.setLifetime(FIREFLY_LIFE + random.nextInt(FIREFLY_LIFE * 3 / 5));
            double far = Math.hypot(x - mc.player.getX(), z - mc.player.getZ()) / FIREFLY_NEAR;
            fly.scale(FIREFLY_SIZE * (float) Math.max(1, far));
            mc.particleEngine.add(fly);
        }
    }

    private static TextureAtlasSprite sprite(Minecraft mc) {
        return ((TextureAtlas) mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES)).getSprite(SPECK);
    }

    private static final class Firefly extends TextureSheetParticle {
        Firefly(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y - 0.125, z, 0, level.random.nextFloat() * -1.9 * level.random.nextFloat() * 0.1, 0);
            setSprite(sprite);
            setSize(0.001f, 0.001f);
            quadSize *= random.nextFloat() * 0.6f + 0.6f;
            hasPhysics = false;
            friction = 1;
            gravity = 0;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
            } else {
                x += xd;
                y += yd;
                z += zd;
            }
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            if (inView(camera, x, y, z)) super.render(buffer, camera, partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    private static boolean floor(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.DARK_OAK_LEAVES) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.OBSIDIAN);
    }
}
