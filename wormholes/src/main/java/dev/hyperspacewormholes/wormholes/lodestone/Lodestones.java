package dev.hyperspacewormholes.wormholes.lodestone;

import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.compat.WaystoneLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.FunctionReference;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Named lodestones as places waystone wormholes lead to. The name is the item's, given in an anvil
 * (LodestoneBlockEntity); dropped lodestones keep it through their loot table, whichever pack it comes from.
 */
public final class Lodestones {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Wormholes.MOD_ID);
    public static final Supplier<BlockEntityType<LodestoneBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("lodestone",
            () -> BlockEntityType.Builder.of(LodestoneBlockEntity::new, Blocks.LODESTONE).build(null));
    private static final ResourceKey<LootItemFunction> KEEP_NAME =
            ResourceKey.create(Registries.ITEM_MODIFIER, Wormholes.id("keep_lodestone_name"));

    private Lodestones() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(Lodestones::keepName);
    }

    private static void keepName(LootTableLoadEvent event) {
        if (event.getKey() != Blocks.LODESTONE.getLootTable()) return;
        LootTable table = event.getTable();
        List<LootItemFunction> functions = new ArrayList<>(table.functions);
        functions.add(FunctionReference.functionReference(KEEP_NAME).build());
        LootTable named = new LootTable(table.getParamSet(), table.randomSequence, table.pools, functions);
        named.setLootTableId(event.getName());
        event.setTable(named);
    }

    /** Every player can travel to every named lodestone. */
    public static List<WaystoneLinks.Target> destinations(ServerPlayer player) {
        MinecraftServer server = player.server;
        return LodestoneIndex.of(server).existing(server).stream()
                .map(entry -> new WaystoneLinks.Target(null, entry.name(), entry.at().dimension(), entry.at().pos())).toList();
    }

    public static Optional<WaystoneLinks.Landing> landing(MinecraftServer server, WaystoneLinks.Target target) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null) return Optional.empty();
        if (named(level, target.pos()) == null) {
            LodestoneIndex.of(server).remove(GlobalPos.of(target.dimension(), target.pos()));
            return Optional.empty();
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = target.pos().relative(side);
            if (hasRoom(level, beside)) return Optional.of(new WaystoneLinks.Landing(level, Vec3.atBottomCenterOf(beside), side.toYRot()));
        }
        return Optional.of(new WaystoneLinks.Landing(level, Vec3.atBottomCenterOf(target.pos().above()), Direction.SOUTH.toYRot()));
    }

    private static boolean hasRoom(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    static @Nullable LodestoneBlockEntity named(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LodestoneBlockEntity lodestone && lodestone.hasCustomName() ? lodestone : null;
    }
}
