package dev.hyperspacewormholes.hyperspace;

import com.bedrockk.molang.runtime.struct.QueryStruct;
import com.cobblemon.mod.common.api.entity.EntitySideDelegate;
import com.cobblemon.mod.common.entity.PosableEntity;
import com.cobblemon.mod.common.entity.PoseType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The floating Poke Balls on Hyperspace islands, plus the golden one you get for finishing every task. They bob
 * around, only pop when a Pokemon's quick-battle move hits them (you can't just punch them), and drop loot and
 * money based on your ring's stars.
 */
public class FloatingBall extends Entity implements PosableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FloatingBall.class, EntityDataSerializers.OPTIONAL_UUID);
    public static Function<FloatingBall, EntitySideDelegate<FloatingBall>> clientDelegate;
    public static Predicate<FloatingBall> seen = ball -> true;

    private EntitySideDelegate<FloatingBall> delegate;
    private QueryStruct struct;
    public static final float SIZE = 0.75f;
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Hyperspace.id("gameplay/floating_poke_ball"));
    public static final float GOLDEN_SCALE = 1.5f;
    public static final long MONEY = 2_500;
    public static final long GOLDEN_MONEY = 10_000;
    public static final ResourceKey<LootTable> GOLDEN_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Hyperspace.id("gameplay/golden_floating_poke_ball"));

    private long born = -1;

    public FloatingBall(EntityType<? extends FloatingBall> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    public boolean belongsTo(Entity entity) {
        return owner().map(id -> entity != null && id.equals(entity.getUUID())).orElse(true);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    public void pop(ServerLevel level) {
        if (isRemoved()) return;
        Vec3 centre = position().add(0, size() / 2, 0);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, centre)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, onlineOwner()).create(LootContextParamSets.CHEST);
        DonutPowers powers = FloatingBalls.powers(level, onlineOwner());
        boolean rich = golden() || random.nextFloat() < powers.itemChance();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(rich ? GOLDEN_LOOT : LOOT);
        List<ItemStack> loot = new ArrayList<>(table.getRandomItems(params));
        for (int i = 0; i < powers.bigHaul(); i++) {
            List<ItemStack> more = table.getRandomItems(params);
            if (!more.isEmpty()) loot.add(more.get(0));
        }
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) spawnAtLocation(stack, size() / 2);
        }
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.5f, 1.4f);
        level.sendParticles(ParticleTypes.POOF, centre.x, centre.y, centre.z, 12, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.93f, 0.2f, 0.2f), 1.5f), centre.x, centre.y + 0.3, centre.z, 16, 0.5, 0.3, 0.5, 0);
        level.sendParticles(new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.5f), centre.x, centre.y - 0.3, centre.z, 16, 0.5, 0.3, 0.5, 0);
        FloatingBalls.popped(this, onlineOwner());
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (born < 0) born = level().getGameTime();
        if (!level().isClientSide() && tickCount % 20 == 0 && Zones.leftover(blockPosition(), born)) discard();
        if (level().isClientSide()) {
            sparkle();
            getDelegate().tick(this);
        }
    }

    public long money() {
        return Math.round((golden() ? GOLDEN_MONEY : MONEY) * HoopaStarsCount.factor(HoopaStars.of(onlineOwner())));
    }

    private ServerPlayer onlineOwner() {
        MinecraftServer server = getServer();
        return server == null ? null : owner().map(server.getPlayerList()::getPlayer).orElse(null);
    }

    public boolean golden() {
        return getType() == FloatingBalls.GOLDEN;
    }

    public float size() {
        return golden() ? SIZE * GOLDEN_SCALE : SIZE;
    }

    @Override
    public PoseType getCurrentPoseType() {
        return PoseType.STAND;
    }

    @Override
    public EntitySideDelegate<FloatingBall> getDelegate() {
        if (delegate == null) {
            delegate = level().isClientSide() && clientDelegate != null ? clientDelegate.apply(this) : new EntitySideDelegate<>() {};
        }
        return delegate;
    }

    @Override
    public QueryStruct getStruct() {
        if (struct == null) struct = new QueryStruct(new HashMap<>());
        return struct;
    }

    private void sparkle() {
        if (random.nextInt(4) != 0 || !seen.test(this)) return;
        double angle = random.nextDouble() * Math.PI * 2, r = size() * (0.7 + random.nextDouble() * 0.5);
        double x = getX() + Math.cos(angle) * r, y = getY() + size() / 2 + (random.nextDouble() - 0.5) * size() * 1.4, z = getZ() + Math.sin(angle) * r;
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
        } else {
            int rgb = golden() ? Mth.hsvToRgb(0.1f + random.nextFloat() * 0.06f, 0.6f, 1f) : Mth.hsvToRgb(random.nextFloat(), 0.45f, 1f);
            level().addParticle(new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), 0.6f), x, y, z, 0, 0, 0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        born = tag.getLong("born");
        if (tag.hasUUID("owner")) setOwner(tag.getUUID("owner"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("born", born);
        owner().ifPresent(id -> tag.putUUID("owner", id));
    }
}
