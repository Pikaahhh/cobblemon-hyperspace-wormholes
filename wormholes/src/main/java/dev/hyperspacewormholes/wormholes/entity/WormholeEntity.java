package dev.hyperspacewormholes.wormholes.entity;

import dev.hyperspacewormholes.wormholes.ride.HomePoint;
import dev.hyperspacewormholes.wormholes.ride.WaysHome;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The wormhole itself. Opened by a command, a legend's roar or a flute. Fly into one on a legend to start an Ultra
 * Warp Ride, or walk into a way home to go back to where your ride started. Handles its colour, rings, size, how long
 * it lasts and sinking away.
 */
public class WormholeEntity extends Entity {
    public static final float SIZE = 7f;
    public static final double SINK_SPEED = 0.2;
    public static final int CLOSE_TICKS = 20;
    private static final EntityDataAccessor<Integer> RINGS =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TYPE =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BEACON =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SINK_TO =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RAINBOW =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.FLOAT);
    private static final int WATER_REACH = 5;
    private static final double MOUTH_RADIUS = 2.6 * 1.7 * 0.8;
    private static final double WELL_DEPTH = 2.6 * 1.8 * 0.6;
    private static final double MOUTH_REACH = 0.8;

    private @Nullable HomePoint returnTo;
    private final Map<UUID, Double> lastAlong = new HashMap<>();
    private boolean singleUse;
    private boolean decorative;
    private boolean nearWater;
    private @Nullable String override;

    public WormholeEntity(EntityType<? extends WormholeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RINGS, 0);
        builder.define(TYPE, 0);
        builder.define(LIFETIME, 0);
        builder.define(BEACON, false);
        builder.define(SINK_TO, Float.NaN);
        builder.define(RAINBOW, false);
        builder.define(SCALE, 1f);
    }

    public float getScale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    public boolean isRainbow() {
        return entityData.get(RAINBOW);
    }

    public void setRainbow(boolean rainbow) {
        entityData.set(RAINBOW, rainbow);
    }

    public void sinkTo(double y) {
        entityData.set(SINK_TO, (float) y);
    }

    public boolean isSinking() {
        float to = entityData.get(SINK_TO);
        return !Float.isNaN(to) && getY() > to + 1e-3;
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public void setLifetime(int ticks) {
        entityData.set(LIFETIME, Math.max(0, ticks));
    }

    public int getRings() {
        return entityData.get(RINGS);
    }

    public void setRings(int rings) {
        entityData.set(RINGS, Mth.clamp(rings, 0, 4));
    }

    public WormholeType getWormholeType() {
        return WormholeType.byId(entityData.get(TYPE));
    }

    public void setWormholeType(WormholeType type) {
        entityData.set(TYPE, type.ordinal());
    }

    public boolean isBeacon() {
        return entityData.get(BEACON);
    }

    public void setBeacon(boolean beacon) {
        entityData.set(BEACON, beacon);
    }

    public void setSingleUse(boolean singleUse) {
        this.singleUse = singleUse;
    }

    public void setOverride(@Nullable String override) {
        this.override = override;
    }

    public void setDecorative(boolean decorative) {
        this.decorative = decorative;
    }

    public void setReturnTo(@Nullable HomePoint returnTo) {
        this.returnTo = returnTo;
    }

    @Override
    public boolean shouldBeSaved() {
        return returnTo == null && !decorative && super.shouldBeSaved();
    }

    public boolean isNearWater() {
        return nearWater;
    }

    private boolean findWater() {
        BlockPos middle = BlockPos.containing(center());
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-WATER_REACH, -WATER_REACH, -WATER_REACH),
                middle.offset(WATER_REACH, WATER_REACH, WATER_REACH))) {
            if (level().getFluidState(pos).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    public Vec3 center() {
        return position().add(0, SIZE / 2, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (isSinking()) setPos(getX(), Math.max(entityData.get(SINK_TO), getY() - SINK_SPEED), getZ());
        if (level().isClientSide) {
            spawnSwirlParticles();
            if (tickCount % 10 == 1) nearWater = findWater();
            return;
        }
        int lifetime = getLifetime();
        if (lifetime > 0 && tickCount >= lifetime) {
            closeAfterUse();
            return;
        }
        if (!decorative && tickCount % 2 == 0) pullInRiders();
    }

    private boolean inMouth(Entity entity) {
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 offset = entity.getBoundingBox().getCenter().subtract(center());
        double along = offset.dot(facing);
        Double before = lastAlong.put(entity.getUUID(), along);
        if (before == null || before < 0) return false;
        if (along > MOUTH_REACH || along < -WELL_DEPTH) return false;
        double across = offset.subtract(facing.scale(along)).length();
        return across <= MOUTH_RADIUS + entity.getBbWidth() / 2;
    }

    private void pullInRiders() {
        AABB box = getBoundingBox();
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, box.inflate(6))) {
            Entity vehicle = player.getVehicle();
            if (returnTo != null) {
                if (tickCount >= 60 && UltraWarpRide.canUseWayHome(player)
                        && inMouth(vehicle != null ? vehicle : player)) {
                    WaysHome.send(player, returnTo);
                    if (closesOnUse()) {
                        closeAfterUse();
                        return;
                    }
                }
                continue;
            }
            boolean touching = player.getBoundingBox().intersects(box)
                    || (vehicle != null && vehicle.getBoundingBox().intersects(box));
            if (!touching || !UltraWarpRide.canEnter(player)) continue;
            Vec3 from = (vehicle != null ? vehicle : player).position();
            Vec3 away = new Vec3(from.x - getX(), 0, from.z - getZ());
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            Vec3 returnPos = new Vec3(getX(), from.y, getZ()).add(away.scale(SIZE / 2 + 5));
            float yaw = (float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90f;

            Vec3 c = center();
            level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2f, 1.6f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.35f, 1.8f);
            UltraWarpRide.start(player, returnPos, yaw, override);
            if (singleUse || closesOnUse()) {
                closeAfterUse();
                return;
            }
        }
    }

    private boolean closesOnUse() {
        return !UltraDimensions.isUltra(level());
    }

    private void closeAfterUse() {
        Vec3 c = center();
        level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.4f);
        discard();
    }

    private void spawnSwirlParticles() {
        RandomSource random = level().random;
        Vec3 c = center();
        WormholeType type = getWormholeType();
        Vector3f colour = new Vector3f(type.r, type.g, type.b);
        if (isRainbow()) {
            int rgb = Mth.hsvToRgb(random.nextFloat(), 0.75f, 1f);
            colour.set((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }
        DustParticleOptions dust = new DustParticleOptions(colour, 1.2f);
        float reach = 4.6f + getRings() * 0.6f;
        for (int i = 0; i < 3; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(random.nextDouble() * 2 - 1);
            double r = reach + random.nextDouble() * 1.5;
            double x = Math.sin(phi) * Math.cos(theta) * r;
            double y = Math.cos(phi) * r;
            double z = Math.sin(phi) * Math.sin(theta) * r;
            level().addParticle(i == 0 ? ParticleTypes.END_ROD : dust,
                    c.x + x, c.y + y, c.z + z, x * -0.07 - z * 0.06, y * -0.07, z * -0.07 + x * 0.06);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    c.x + random.triangle(0, 2), c.y + random.triangle(0, 2), c.z + random.triangle(0, 2), 0, 0, 0);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = getBoundingBox().inflate(3);
        return isBeacon() ? box.expandTowards(0, level().getMaxBuildHeight() - getY(), 0) : box;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return isBeacon() || distance < 192 * 192;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRings(tag.getInt("Rings"));
        setWormholeType(WormholeType.byId(tag.getInt("Type")));
        singleUse = tag.getBoolean("SingleUse");
        setLifetime(tag.getInt("LifeLeft"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Rings", getRings());
        tag.putInt("Type", getWormholeType().ordinal());
        if (singleUse) tag.putBoolean("SingleUse", true);
        if (getLifetime() > 0) tag.putInt("LifeLeft", Math.max(1, getLifetime() - tickCount));
    }
}
