package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The look of a spatial distortion and of the portal Hoopa opens there, as one unsaved entity 1.5 blocks up in front of
 * the wall, drawn by RiftRenderer. It only carries timing in game ticks (formed or opened, fading or closing, last
 * stirred by Hoopa) so every player sees the same frame, plus the unstable and locked flags and a distortion's label.
 * The distortion's own record is what's saved, so rifts come back when its chunk loads again.
 */
public class Rift extends Entity {
    public static final EntityType<Rift> TYPE = register("rift", EntityType.Builder.<Rift>of(Rift::new, MobCategory.MISC)
            .sized(0.5f, 0.5f).fireImmune().noSave().noSummon().clientTrackingRange(10).updateInterval(20));
    public static final double HEIGHT = 1.5;
    public static final int APPEAR = 67;
    public static final int FADE = 100;
    public static final int OPENING = 61;
    public static final int GATE = 45;
    public static final int CLOSING = 20;
    public static final int STIR = 200;
    public static final int LOCK_TIME = 20;
    private static final EntityDataAccessor<Boolean> PORTAL = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> SINCE = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> ENDS = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> STIRRED = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> UNSTABLE = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> LOCKED_AT = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Component> LABEL = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.COMPONENT);
    float labelFade;
    double labelAt;

    public Rift(EntityType<? extends Rift> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void register() {
    }

    private static EntityType<Rift> register(String name, EntityType.Builder<Rift> builder) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, Hyperspace.id(name), builder.build(name));
    }

    public static Rift distortion(ServerLevel level, Vec3 base, Vec3 normal, long born, Component label) {
        return spawn(level, base, normal, false, born, false, label);
    }

    public static Rift portal(ServerLevel level, Vec3 base, Vec3 normal, boolean unstable) {
        return spawn(level, base, normal, true, level.getGameTime(), unstable, Component.empty());
    }

    /** A portal that's already fully open, for the one you've just stepped out of. */
    public static Rift opened(ServerLevel level, Vec3 base, Vec3 normal, boolean unstable) {
        return spawn(level, base, normal, true, level.getGameTime() - OPENING, unstable, Component.empty());
    }

    private static Rift spawn(ServerLevel level, Vec3 base, Vec3 normal, boolean portal, long since, boolean unstable, Component label) {
        Rift rift = TYPE.create(level);
        rift.setPos(base.x, base.y + HEIGHT, base.z);
        rift.setYRot((float) Math.toDegrees(Math.atan2(-normal.x, normal.z)));
        rift.entityData.set(PORTAL, portal);
        rift.entityData.set(SINCE, since);
        rift.entityData.set(UNSTABLE, unstable);
        rift.entityData.set(LABEL, label);
        level.addFreshEntity(rift);
        return rift;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PORTAL, false);
        builder.define(SINCE, 0L);
        builder.define(ENDS, 0L);
        builder.define(STIRRED, 0L);
        builder.define(UNSTABLE, false);
        builder.define(LOCKED, false);
        builder.define(LOCKED_AT, 0L);
        builder.define(LABEL, Component.empty());
    }

    public boolean portal() {
        return entityData.get(PORTAL);
    }

    public long since() {
        return entityData.get(SINCE);
    }

    public long ends() {
        return entityData.get(ENDS);
    }

    public long stirred() {
        return entityData.get(STIRRED);
    }

    public boolean unstable() {
        return entityData.get(UNSTABLE);
    }

    public boolean locked() {
        return entityData.get(LOCKED);
    }

    public long lockedAt() {
        return entityData.get(LOCKED_AT);
    }

    public Component label() {
        return entityData.get(LABEL);
    }

    public void lock(boolean locked) {
        if (locked == locked()) return;
        entityData.set(LOCKED, locked);
        entityData.set(LOCKED_AT, level().getGameTime());
    }

    public boolean open() {
        return portal() && ends() == 0 && level().getGameTime() - since() >= OPENING;
    }

    public void end() {
        if (ends() != 0) return;
        entityData.set(ENDS, level().getGameTime());
        if (portal()) level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.6f);
    }

    public void stir() {
        entityData.set(STIRRED, level().getGameTime());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        long now = level().getGameTime();
        if (portal() && ends() == 0 && now - since() == GATE) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.5f);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.7f);
        }
        if (ends() != 0 && now - ends() > (portal() ? CLOSING : FADE) + 2) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192 * 192;
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
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
