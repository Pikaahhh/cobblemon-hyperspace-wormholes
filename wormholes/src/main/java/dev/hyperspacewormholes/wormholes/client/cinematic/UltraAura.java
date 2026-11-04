package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stat-boosting aura Ultra Necrozma carries through its battle in Ultra Sun and Ultra Moon: gold sparks rising off
 * its whole body as flat square pixels in three hard colour steps. It switches on at the power-up's frame AURA_ON and
 * stays while its stats are raised. Sparks start from points spread over the posed model (PosableModelCinematicMixin)
 * and climb RISE times the model's size, so the aura follows its pose.
 */
public final class UltraAura {
    private static final int SPARKS = 110, CUBE_STEP = 2, STEPS = 7;
    private static final double RISE = 0.2, PIXEL = 0.011;
    private static final float[][] COLOURS = {{1f, 0.97f, 0.72f}, {1f, 0.8f, 0.22f}, {0.93f, 0.52f, 0.1f}};
    private static final float[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
    private static final Map<Integer, Body> BODIES = new HashMap<>();
    private static final Set<Integer> POWERED = new HashSet<>();
    private static boolean registered;

    private record Body(List<Vec3> points, double size, long frame) {}

    private UltraAura() {}

    public static void register() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.LAST.register(UltraAura::collect);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                BODIES.clear();
                POWERED.clear();
                return;
            }
            POWERED.removeIf(id -> !(client.level.getEntity(id) instanceof PokemonEntity p) || p.isRemoved() || !ultra(p));
            BODIES.keySet().removeIf(id -> !(client.level.getEntity(id) instanceof PokemonEntity p) || !shows(p));
        });
    }

    static void powered(int entityId) {
        POWERED.add(entityId);
    }

    static void release(int entityId) {
        POWERED.remove(entityId);
    }

    private static boolean ultra(PokemonEntity pokemon) {
        return pokemon.getEntityData().get(PokemonEntity.getASPECTS()).contains("ultra-fusion");
    }

    static boolean shows(PokemonEntity pokemon) {
        if (pokemon.getOwner() != null || !ultra(pokemon) || UltraBurstCinematic.isBoss(pokemon)) return false;
        if (NecrozmaSceneCinematic.isBoss(pokemon)) {
            return NecrozmaSceneCinematic.kind() != NecrozmaScenePayload.DEFEAT
                    && NecrozmaSceneCinematic.frame(0) >= PowerUpEffects.AURA_ON;
        }
        return POWERED.contains(pokemon.getId()) || pokemon.isBattling();
    }

    public static void track(Entity entity, PosableModel model, PosableState state) {
        if (!(entity instanceof PokemonEntity pokemon) || !shows(pokemon)) return;
        MatrixWrapper root = state.getLocatorStates().get("root");
        if (root == null) return;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(root.getMatrix()).rotateZ(-180f * Mth.DEG_TO_RAD);
        List<Vec3> points = new ArrayList<>();
        Vector3f centre = new Vector3f();
        int[] count = {0};
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE};
        Vec3 at = entity.getPosition(state.getPartialTicks());
        for (Map.Entry<String, Bone> top : model.getRootPart().getChildren().entrySet()) {
            if (!(top.getValue() instanceof ModelPart part)) continue;
            part.visit(pose, (p, path, index, cube) -> {
                if (count[0]++ % CUBE_STEP != 0) return;
                p.pose().transformPosition((cube.minX + cube.maxX) / 32f, (cube.minY + cube.maxY) / 32f, (cube.minZ + cube.maxZ) / 32f, centre);
                points.add(at.add(centre.x, centre.y, centre.z));
                box[0] = Math.min(box[0], centre.y);
                box[1] = Math.max(box[1], centre.y);
            });
        }
        if (points.isEmpty()) return;
        BODIES.put(entity.getId(), new Body(points, Math.max(1, box[1] - box[0]), Minecraft.getInstance().level.getGameTime()));
    }

    private static void collect(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BODIES.isEmpty()) return;
        Vec3 cam = context.camera().getPosition();
        var orientation = context.camera().rotation();
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0)), up = orientation.transform(new Vector3f(0, 1, 0));
        double time = mc.level.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        List<float[]> quads = new ArrayList<>();
        for (Map.Entry<Integer, Body> entry : BODIES.entrySet()) {
            Body body = entry.getValue();
            if (mc.level.getGameTime() - body.frame > 2) continue;
            RandomSource random = RandomSource.create(entry.getKey() * 31L);
            double pixel = PIXEL * body.size;
            for (int i = 0; i < SPARKS; i++) {
                Vec3 from = body.points.get(random.nextInt(body.points.size()));
                double period = 16 + random.nextInt(12), phase = random.nextInt(32);
                int big = random.nextInt(3) == 0 ? 2 : 1;
                double dx = (random.nextInt(5) - 2) * pixel, dz = (random.nextInt(5) - 2) * pixel;
                double rise = RISE * body.size * (0.6 + random.nextDouble() * 0.8);
                double cycle = (time + phase) / period;
                int round = (int) Math.floor(cycle);
                double life = cycle - round;
                int step = (int) (life * STEPS);
                if (Math.floorMod(round * 7 + i * 13 + (int) (time / 3), 9) == 0) continue;
                double y = rise * step / STEPS;
                double x = dx + Math.floorMod(round * 5 + i, 3) * pixel - pixel, z = dz;
                float[] colour = COLOURS[Math.min(COLOURS.length - 1, step * COLOURS.length / STEPS)];
                double half = pixel * big * 0.5;
                Vec3 c = from.add(x, y, z).subtract(cam);
                quads.add(new float[]{(float) c.x, (float) c.y, (float) c.z, (float) half, colour[0], colour[1], colour[2]});
            }
        }
        if (quads.isEmpty()) return;
        float rx = right.x, ry = right.y, rz = right.z, ux = up.x, uy = up.y, uz = up.z;
        RenderType type = RenderType.debugQuads();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(type);
        for (float[] q : quads) {
            float h = q[3];
            for (int v = 0; v < 8; v++) {
                float[] k = CORNERS[v < 4 ? v : 7 - v];
                buffer.addVertex(q[0] + (rx * k[0] + ux * k[1]) * h, q[1] + (ry * k[0] + uy * k[1]) * h,
                        q[2] + (rz * k[0] + uz * k[1]) * h).setColor(q[4], q[5], q[6], 0.9f);
            }
        }
        buffers.endBatch(type);
    }
}
