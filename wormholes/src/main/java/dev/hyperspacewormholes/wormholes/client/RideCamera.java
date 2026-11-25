package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.RideLegend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Ultra Warp Ride's camera is, which way it looks and how wide it sees. It sits behind and above the legend,
 * steady against its flying animation, at the angle Ultra Sun and Ultra Moon use: 1250 game units back and 430 up,
 * looking at a point 1000 ahead and 100 up, scaled so it is DISTANCE blocks back (FUSED_DISTANCE for Dusk Mane and Dawn
 * Wings Necrozma). It sees FOV degrees whatever the player's setting. Going into a wormhole WarpEntryCamera moves it.
 * <p>
 * During the opening shot RideOpening places and aims it instead, and as the shot ends it glides into the ride's place
 * (RideOpening.toRide).
 */
public final class RideCamera {
    private static final float FOV = 95f;
    private static final float DISTANCE = 11f;
    private static final float FUSED_DISTANCE = 13f;
    private static final float PIVOT = 0.6f;
    private static final float GAME_BACK = 1250f;
    private static final float GAME_UP = 430f;
    private static final float GAME_AIM_AHEAD = 1000f;
    private static final float GAME_AIM_UP = 100f;
    /** How many blocks tall the opening's picture is at a legend 1250 game units away. */
    private static final float OPENING_HEIGHT = 13.6f;
    /** The point of a legend the opening's numbers are measured from, in game units above its hitbox's middle. */
    private static final float LUNALA_ANCHOR = -40f;
    private static final float SOLGALEO_ANCHOR = -110f;

    public record View(Vec3 eye, float yaw, float pitch) {}

    private RideCamera() {}

    /** Blocks to a game unit in the opening shot. */
    public static float unit(Entity body) {
        float size = fused(body) ? FUSED_DISTANCE / DISTANCE : 1f;
        return OPENING_HEIGHT / (2f * tan(RideOpening.FOV) * GAME_BACK) * size;
    }

    private static float tan(float fov) {
        return (float) Math.tan(Math.toRadians(fov / 2));
    }

    public static boolean fused(@Nullable Entity body) {
        return body instanceof PokemonEntity mount && (mount.getPokemon().getAspects().contains("dusk-fusion")
                || mount.getPokemon().getAspects().contains("dawn-fusion"));
    }

    private static float distance(@Nullable Entity body) {
        return fused(body) ? FUSED_DISTANCE : DISTANCE;
    }

    /** How far behind the legend being ridden the ride's camera is. */
    static float distance() {
        LocalPlayer player = Minecraft.getInstance().player;
        return distance(player == null ? null : player.getVehicle());
    }

    /** How far above looking straight at the middle of the legend it would be, from this many times the distance. */
    static float above(float further) {
        LocalPlayer player = Minecraft.getInstance().player;
        float height = player == null || player.getVehicle() == null ? 0f : player.getVehicle().getBbHeight();
        return distance() * (GAME_UP / GAME_BACK - further * (GAME_UP - GAME_AIM_UP) / (GAME_BACK + GAME_AIM_AHEAD))
                + (PIVOT - 0.5f) * height;
    }

    public static float fov(float partialTick) {
        float toRide = RideOpening.toRide(partialTick);
        if (toRide >= 1f) return FOV;
        return (float) Math.toDegrees(2 * Math.atan(share(toRide, tan(RideOpening.FOV), tan(FOV))));
    }

    public static View view(Entity body, float partialTick) {
        Vec3 at = body.getPosition(partialTick);
        float distance = distance(body);
        float lift = distance * GAME_UP / GAME_BACK;
        double pivot = body.getBbHeight() * PIVOT;
        float pitch = (float) Math.toDegrees(Math.atan2(GAME_UP - GAME_AIM_UP, GAME_BACK + GAME_AIM_AHEAD));
        float toRide = RideOpening.toRide(partialTick);
        if (toRide >= 1f) {
            return new View(at.add(0, pivot, 0).add(WarpEntryCamera.offset(partialTick)).add(0, lift, -distance), 0f, pitch);
        }
        boolean solgaleo = RideClient.mount() == RideLegend.SOLGALEO;
        float unit = unit(body);
        float[] shot = RideOpening.camera(RideOpening.frame(partialTick), solgaleo);
        double dx = shot[3] - shot[0], dy = shot[4] - shot[1], dz = shot[5] - shot[2];
        float shotYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float shotPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        double anchor = body.getBbHeight() * 0.5 + (solgaleo ? SOLGALEO_ANCHOR : LUNALA_ANCHOR) * unit;
        if (toRide <= 0f) {
            return new View(at.add(shot[0] * unit, anchor + shot[1] * unit, shot[2] * unit), shotYaw, shotPitch);
        }
        float back = share(toRide, -shot[2] * unit, distance);
        float x = Mth.lerp(toRide, shot[0] / -shot[2], 0f) * back;
        float y = Mth.lerp(toRide, shot[1] / -shot[2], lift / distance) * back;
        return new View(at.add(x, Mth.lerp(toRide, anchor, pivot) + y, -back), Mth.lerp(toRide, shotYaw, 0f),
                Mth.lerp(toRide, shotPitch, pitch));
    }

    /** Between two sizes, changing by the same share of itself all the way. */
    private static float share(float t, float from, float to) {
        return from * (float) Math.pow(to / from, t);
    }
}
