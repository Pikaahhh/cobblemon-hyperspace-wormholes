package dev.hyperspacewormholes.wormholes.lodestone;

import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A lodestone's name, kept the way vanilla's banners and beacons keep theirs: taken from the placed item, saved as
 * CustomName, and given back to the item it drops or is picked as. Sent to clients, as they make the ctrl-picked item.
 * The index hears of every lodestone that loads, is placed or is renamed.
 */
public class LodestoneBlockEntity extends BlockEntity implements Nameable {
    private @Nullable Component name;

    /** The client's own, for LodestoneLabelRenderer. */
    public float labelFade;
    public double labelAt;

    public LodestoneBlockEntity(BlockPos pos, BlockState state) {
        super(Lodestones.BLOCK_ENTITY, pos, state);
    }

    @Override
    public Component getName() {
        return name != null ? name : getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return name;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        index();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.contains("CustomName", Tag.TAG_STRING) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
        index();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (name != null) tag.putString("CustomName", Component.Serializer.toJson(name, registries));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        name = components.get(DataComponents.CUSTOM_NAME);
        index();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, name);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("CustomName");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private void index() {
        if (level instanceof ServerLevel server && !UltraDimensions.isUltra(server)) {
            LodestoneIndex.of(server.getServer()).set(GlobalPos.of(server.dimension(), worldPosition), name);
        }
    }
}
