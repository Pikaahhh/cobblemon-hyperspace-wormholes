package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.MonitorBlock;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.BigBattery;
import dev.hyperspacewormholes.wormholes.CosmicJar;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.WormholesAdvancements;
import dev.hyperspacewormholes.wormholes.mixin.TextDisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

/**
 * The Item Analyser. Cobblemon's Fossil Analyzer with a Data Monitor on top. Load it with a type gem and Blaze Powder
 * (or a full Cosmic Jar and a golden apple), power it with lightning or a Big Battery, and it pulls a Pokemon through a wormhole. Handles its
 * screens, sounds, overheating and burnt-out monitors.
 */
public final class ItemAnalyser {
    private static final ResourceLocation ANALYZER = ResourceLocation.fromNamespaceAndPath("cobblemon", "fossil_analyzer");
    private static final ResourceLocation MONITOR = ResourceLocation.fromNamespaceAndPath("cobblemon", "monitor");
    private static final ResourceLocation DAMAGED_MONITOR = ResourceLocation.fromNamespaceAndPath("cobblemon", "damaged_monitor");
    private static final ResourceLocation PC_ON = ResourceLocation.fromNamespaceAndPath("cobblemon", "pc.on");
    private static final ResourceLocation PC_OFF = ResourceLocation.fromNamespaceAndPath("cobblemon", "pc.off");
    private static final TagKey<Item> TYPE_GEMS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "type_gems"));
    private static final MonitorBlock.MonitorScreen READY_SCREEN = MonitorBlock.MonitorScreen.BLUE_PROGRESS_9;

    private static final int DISTANCE = 5;
    private static final int MAX_RISE = 4;
    private static final double LIGHTNING_BREAK_CHANCE = 0.90;
    private static final long OVERHEAT_TICKS = 5 * 60 * 20;
    private static final long ERROR_TICKS = 5 * 20;
    private static final double BLAZE_CHANCE = 0.05;

    private static final String DISPLAY_TAG = "hyperspace_wormholes_cloning";
    private static final float DISPLAY_SCALE = 0.875f;
    private static final double DISPLAY_STACK = 0.035;
    private static final String COOLDOWN_TAG = "hyperspace_wormholes_cooldown";
    private static final float TIMER_SCALE = 0.6f;
    private static final double TIMER_OUT = 0.5 - 1.6 / 16;
    private static final double SCREEN_MIDDLE = 10 / 16.0;
    private static final double TIMER_MIDDLE = 11.5 * 0.025;
    private static final double SMOKE_SPREAD = 0.15;
    private static final double SMOKE_RISE = 0.05;

    public static final Item ERROR_SCREEN = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("error_screen"),
            new Item(new Item.Properties()));

    private enum Recipe { BLAZE, COSMOG }

    private record Running(ServerLevel level, BlockPos monitor, double breakChance, int closesIn) {}

    private record Hiss(ServerLevel level, BlockPos monitor, int in) {}

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<Hiss> HISSES = new ArrayList<>();

    private ItemAnalyser() {}

    public static void register() {}

    public static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        ItemStack stack = player.getItemInHand(hand);
        BlockPos analyser = pos.below();
        if (is(level.getBlockState(pos), MONITOR) && hand == InteractionHand.MAIN_HAND
                && !(stack.getItem() instanceof BlockItem) && !player.isSecondaryUseActive()
                && level instanceof ServerLevel server && is(level.getBlockState(analyser), ANALYZER)) {
            Store store = Store.of(server);
            Loaded loaded = store.devices.get(analyser.asLong());
            if (loaded == null || loaded.recipe() == null) return InteractionResult.PASS;
            if (ready(server, analyser, store) && batteryPowered(server, analyser)) {
                activate(server, analyser, loaded, store, player, 0);
                return InteractionResult.CONSUME;
            }
            if (overheated(server, analyser, store)) {
                store.errorUntil.put(analyser.asLong(), now(server) + ERROR_TICKS);
                server.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
                showCooldown(server, analyser, store);
            } else {
                refuse(server, pos);
            }
            return InteractionResult.CONSUME;
        }
        if (!is(level.getBlockState(pos), ANALYZER)) return InteractionResult.PASS;
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) return takeBack(player, level, pos);
        boolean core = isCore(stack);
        if (!core && !isPartner(stack)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        if (stack.is(CosmicJar.ITEM) && !CosmicJar.isFull(stack)) {
            refuse(server, pos);
            return InteractionResult.CONSUME;
        }
        Store store = Store.of(server);
        Loaded loaded = store.devices.computeIfAbsent(pos.asLong(), p -> new Loaded());
        ItemStack held = core ? loaded.core : loaded.partner;
        ItemStack other = core ? loaded.partner : loaded.core;
        if (!held.isEmpty() || (!other.isEmpty() && recipeFor(core ? stack : other, core ? other : stack) == null)) {
            refuse(server, pos);
            return InteractionResult.CONSUME;
        }
        if (core) loaded.core = stack.copyWithCount(1);
        else loaded.partner = stack.copyWithCount(1);
        loaded.partnerLast = !core;
        stack.consume(1, player);
        store.setDirty();
        server.playSound(null, pos, CobblemonSounds.FOSSIL_MACHINE_INSERT_FOSSIL, SoundSource.BLOCKS, 1f, 1f);

        showContents(server, pos, loaded);
        refreshScreen(server, pos);
        return InteractionResult.CONSUME;
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    private static boolean overheated(ServerLevel level, BlockPos pos, Store store) {
        Long cooledAt = store.overheated.get(pos.asLong());
        return cooledAt != null && now(level) < cooledAt;
    }

    private static boolean isRunning(Level level, BlockPos monitor) {
        return RUNNING.stream().anyMatch(r -> r.level() == level && r.monitor().equals(monitor));
    }

    private static void refuse(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    private static boolean batteryPowered(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BigBattery.BLOCK);
    }

    public static void onLightning(Entity entity, ServerLevel level) {
        if (!(entity instanceof LightningBolt bolt)) return;
        for (int down = 0; down <= 2; down++) {
            BlockPos rod = bolt.blockPosition().below(down);
            if (!level.getBlockState(rod).is(Blocks.LIGHTNING_ROD)) continue;
            BlockPos analyser = rod.below(2);
            if (!is(level.getBlockState(rod.below()), MONITOR) || !is(level.getBlockState(analyser), ANALYZER)) return;
            Store store = Store.of(level);
            Loaded loaded = store.devices.get(analyser.asLong());
            if (loaded == null || !ready(level, analyser, store)) return;
            Player cause = bolt.getCause() != null ? bolt.getCause()
                    : level.getNearestPlayer(analyser.getX() + 0.5, analyser.getY(), analyser.getZ() + 0.5, 64, false);
            activate(level, analyser, loaded, store, cause, LIGHTNING_BREAK_CHANCE);
            return;
        }
    }

    private static boolean ready(ServerLevel level, BlockPos pos, Store store) {
        if (overheated(level, pos, store)) return false;
        Loaded loaded = store.devices.get(pos.asLong());
        return loaded != null && loaded.recipe() != null;
    }

    private static void refreshScreen(ServerLevel level, BlockPos pos) {
        BlockPos monitorPos = pos.above();
        BlockState monitor = level.getBlockState(monitorPos);
        if (!is(monitor, MONITOR) || !monitor.hasProperty(MonitorBlock.Companion.getSCREEN())) return;
        if (isRunning(level, monitorPos)) return;
        MonitorBlock.MonitorScreen current = monitor.getValue(MonitorBlock.Companion.getSCREEN());
        if (ready(level, pos, Store.of(level))) {
            if (current != READY_SCREEN) level.setBlockAndUpdate(monitorPos, withScreen(monitor, READY_SCREEN));
        } else if (current == READY_SCREEN) {
            level.setBlockAndUpdate(monitorPos, withScreen(monitor, MonitorBlock.MonitorScreen.OFF));
        }
    }

    private static InteractionResult takeBack(Player player, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
        Store store = Store.of(server);
        Loaded loaded = store.devices.get(pos.asLong());
        if (loaded == null || (loaded.core.isEmpty() && loaded.partner.isEmpty())) return InteractionResult.PASS;
        boolean partner = !loaded.partner.isEmpty() && (loaded.partnerLast || loaded.core.isEmpty());
        player.setItemInHand(InteractionHand.MAIN_HAND, partner ? loaded.partner : loaded.core);
        if (partner) loaded.partner = ItemStack.EMPTY;
        else loaded.core = ItemStack.EMPTY;
        if (loaded.core.isEmpty() && loaded.partner.isEmpty()) store.devices.remove(pos.asLong());
        store.setDirty();
        server.playSound(null, pos, CobblemonSounds.FOSSIL_MACHINE_RETRIEVE_FOSSIL, SoundSource.BLOCKS, 1f, 1f);
        showContents(server, pos, loaded);
        refreshScreen(server, pos);
        return InteractionResult.SUCCESS;
    }

    private static boolean isCore(ItemStack stack) {
        return stack.is(TYPE_GEMS) || stack.is(CosmicJar.ITEM);
    }

    private static boolean isPartner(ItemStack stack) {
        return stack.is(Items.BLAZE_POWDER) || stack.is(Items.GOLDEN_APPLE);
    }

    private static @Nullable Recipe recipeFor(ItemStack core, ItemStack partner) {
        if (core.is(TYPE_GEMS) && partner.is(Items.BLAZE_POWDER)) return Recipe.BLAZE;
        if (core.is(CosmicJar.ITEM) && partner.is(Items.GOLDEN_APPLE)) return Recipe.COSMOG;
        return null;
    }

    private static void showContents(ServerLevel level, BlockPos pos, @Nullable Loaded loaded) {
        level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos),
                e -> e.getTags().contains(DISPLAY_TAG)).forEach(Entity::discard);
        if (loaded == null) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return;
        float yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot();
        Vec3 at = Vec3.atBottomCenterOf(pos).add(0, 6 / 16.0 + 0.02, 0);
        for (ItemStack item : List.of(loaded.core, loaded.partner)) {
            if (item.isEmpty()) continue;
            CompoundTag tag = displayTag(at, yaw, floats(-0.70710677f, 0, 0, 0.70710677f), DISPLAY_SCALE);
            tag.put("item", item.save(level.registryAccess()));
            spawn(level, EntityType.ITEM_DISPLAY, tag, DISPLAY_TAG);
            at = at.add(0, DISPLAY_STACK, 0);
        }
    }

    private static void activate(ServerLevel level, BlockPos pos, Loaded loaded, Store store, @Nullable Player player,
                                 double breakChance) {
        BlockPos monitorPos = pos.above();
        BlockState monitor = level.getBlockState(monitorPos);
        if (!is(monitor, MONITOR)) {
            refuse(level, pos);
            return;
        }
        if (isRunning(level, monitorPos)) return;
        if (player instanceof ServerPlayer builder) WormholesAdvancements.machine(builder);
        Direction screen = monitor.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        Recipe recipe = loaded.recipe();
        String type = recipe == Recipe.COSMOG ? "psychic" : typeOf(loaded.core.getItem());
        boolean hasUltraBeast = recipe != Recipe.COSMOG && WormholePokemon.ultraBeastOfType(type, level.getRandom()) != null;
        double chance = recipe == Recipe.BLAZE ? BLAZE_CHANCE : 0;
        boolean ultraBeast = hasUltraBeast && level.getRandom().nextDouble() < chance;
        String properties = recipe == Recipe.COSMOG ? "cosmog level=5"
                : ultraBeast ? WormholePokemon.ultraBeastOfType(type, level.getRandom())
                : WormholePokemon.regularOfType(type, level.getRandom());
        if (properties == null) return;

        PokemonEntity entity;
        try {
            entity = PokemonProperties.Companion.parse(properties).createEntity(level);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't make the Item Analyser's Pokémon '{}'", properties, e);
            return;
        }
        WormholePokemon.atLeastAverageSize(entity.getPokemon());
        entity.getPokemon().setShiny(WormholePokemon.analyserShiny(entity.getPokemon(),
                player instanceof ServerPlayer sp ? sp : null, level.getRandom()));
        entity.refreshDimensions();
        Direction behind = screen.getOpposite();
        float yaw = screen.toYRot();
        Vec3 landing = landing(level, monitorPos.relative(behind, DISTANCE), behind, entity, yaw);
        if (landing == null) {
            refuse(level, pos);
            return;
        }

        store.devices.remove(pos.asLong());
        store.overheated.put(pos.asLong(), now(level) + OVERHEAT_TICKS);
        showContents(level, pos, null);
        store.setDirty();
        if (recipe == Recipe.COSMOG) Block.popResource(level, pos.above(2), new ItemStack(CosmicJar.ITEM));
        PokemonArrivals.start(level, entity, landing, yaw,
                ultraBeast || recipe == Recipe.COSMOG ? WormholeType.WHITE : WormholeType.GREEN, false);
        if (player instanceof ServerPlayer serverPlayer) CosmicJar.maybeCharge(serverPlayer);
        if (player instanceof ServerPlayer summoner) {
            if (recipe == Recipe.COSMOG) WormholesAdvancements.cosmog(summoner);
            else WormholesAdvancements.summoned(summoner);
        }
        level.setBlockAndUpdate(monitorPos, withScreen(monitor, screenFor(type)));
        level.playSound(null, monitorPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1.4f);
        level.playSound(null, monitorPos, SoundEvent.createVariableRangeEvent(PC_ON), SoundSource.BLOCKS, 1.5f, 0.5f);
        RUNNING.add(new Running(level, monitorPos, breakChance, PokemonArrivals.TICKS_UNTIL_CLOSED));
    }

    public static boolean canBreak(Level level, BlockPos pos) {
        return !isRunning(level, pos) && !isRunning(level, pos.above());
    }

    private static void burnOut(ServerLevel level, BlockPos monitor) {
        Vec3 c = Vec3.atCenterOf(monitor);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 8, 0.25, 0.2, 0.25, 0.02);
        level.playSound(null, monitor, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 1.3f);
        HISSES.add(new Hiss(level, monitor, 12));
    }

    private static void cooledDown(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            Store store = Store.of(level);
            for (Iterator<Map.Entry<Long, Long>> it = store.overheated.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Long, Long> hot = it.next();
                BlockPos pos = BlockPos.of(hot.getKey());
                if (!level.isLoaded(pos)) continue;
                if (now >= hot.getValue()) {
                    it.remove();
                    store.setDirty();
                    refreshScreen(level, pos);
                }
                showCooldown(level, pos, store);
            }
        }
    }

    private static void errorsShown(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            Store store = Store.of(level);
            for (Iterator<Map.Entry<Long, Long>> it = store.errorUntil.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Long, Long> shown = it.next();
                BlockPos pos = BlockPos.of(shown.getKey());
                if (now < shown.getValue() || !level.isLoaded(pos)) continue;
                it.remove();
                showCooldown(level, pos, store);
            }
        }
    }

    private static void showCooldown(ServerLevel level, BlockPos pos, Store store) {
        BlockPos monitorPos = pos.above();
        List<Display> shown = level.getEntitiesOfClass(Display.class, new AABB(monitorPos),
                e -> e.getTags().contains(COOLDOWN_TAG));
        long now = now(level);
        Long cooledAt = store.overheated.get(pos.asLong());
        long left = cooledAt == null ? 0 : cooledAt - now;
        BlockState monitor = level.getBlockState(monitorPos);
        Long until = store.errorUntil.get(pos.asLong());
        boolean show = left > 0 && until != null && now < until && is(monitor, MONITOR) && is(level.getBlockState(pos), ANALYZER)
                && monitor.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && !isRunning(level, monitorPos);
        if (!show) {
            shown.forEach(Entity::discard);
            return;
        }
        long seconds = (left + 19) / 20;
        Component text = Component.literal("Error\n" + seconds / 60 + ":" + String.format("%02d", seconds % 60));
        if (shown.size() == 2 && shown.stream().anyMatch(e -> e instanceof Display.TextDisplay)) {
            shown.stream().filter(e -> e instanceof Display.TextDisplay)
                    .forEach(e -> ((TextDisplayAccessor) e).hyperspace_wormholes$setText(text));
            return;
        }
        shown.forEach(Entity::discard);
        Direction screen = monitor.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        Vec3 centre = Vec3.atCenterOf(monitorPos);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("sky", 15);
        brightness.putInt("block", 15);

        CompoundTag red = displayTag(centre, screen.toYRot(), floats(0, 0, 0, 1), 1f);
        red.put("brightness", brightness);
        red.put("item", new ItemStack(ERROR_SCREEN).save(level.registryAccess()));
        spawn(level, EntityType.ITEM_DISPLAY, red, COOLDOWN_TAG);

        Vec3 at = centre.add(Vec3.atLowerCornerOf(screen.getNormal()).scale(TIMER_OUT))
                .add(0, SCREEN_MIDDLE - 0.5 - TIMER_MIDDLE * TIMER_SCALE, 0);
        CompoundTag timer = displayTag(at, screen.toYRot(), floats(0, 0, 0, 1), TIMER_SCALE);
        timer.put("brightness", brightness);
        timer.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        timer.putInt("background", 0);
        spawn(level, EntityType.TEXT_DISPLAY, timer, COOLDOWN_TAG);
    }

    private static CompoundTag displayTag(Vec3 at, float yaw, ListTag leftRotation, float scale) {
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(at.x));
        pos.add(DoubleTag.valueOf(at.y));
        pos.add(DoubleTag.valueOf(at.z));
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", leftRotation);
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        transformation.put("translation", floats(0, 0, 0));
        transformation.put("scale", floats(scale, scale, scale));
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", pos);
        tag.put("Rotation", floats(yaw, 0));
        tag.put("transformation", transformation);
        return tag;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private static void spawn(ServerLevel level, EntityType<? extends Display> type, CompoundTag tag, String marker) {
        Display display = type.create(level);
        CompoundTag full = new CompoundTag();
        display.saveWithoutId(full);
        display.load(full.merge(tag));
        display.addTag(marker);
        level.addFreshEntity(display);
    }

    private static void smoke(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            for (Map.Entry<Long, Long> hot : Store.of(level).overheated.entrySet()) {
                BlockPos monitor = BlockPos.of(hot.getKey()).above();
                if (now >= hot.getValue() || !level.isLoaded(monitor)) continue;
                BlockState state = level.getBlockState(monitor);
                if (!is(state, MONITOR) && !is(state, DAMAGED_MONITOR)) continue;
                double x = monitor.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * SMOKE_SPREAD;
                double z = monitor.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * SMOKE_SPREAD;
                level.sendParticles(ParticleTypes.SMOKE, x, monitor.getY() + 1.02, z, 0, 0, 1, 0, SMOKE_RISE);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        for (ListIterator<Hiss> it = HISSES.listIterator(); it.hasNext(); ) {
            Hiss h = it.next();
            if (h.in() > 1) {
                it.set(new Hiss(h.level(), h.monitor(), h.in() - 1));
                continue;
            }
            it.remove();
            h.level().playSound(null, h.monitor(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
        }
        if (server.getTickCount() % 20 == 0) cooledDown(server);
        if (server.getTickCount() % 3 == 0) smoke(server);
        errorsShown(server);
        for (ListIterator<Running> it = RUNNING.listIterator(); it.hasNext(); ) {
            Running r = it.next();
            if (r.closesIn() > 1) {
                it.set(new Running(r.level(), r.monitor(), r.breakChance(), r.closesIn() - 1));
                continue;
            }
            it.remove();
            ServerLevel level = r.level();
            BlockState monitor = level.getBlockState(r.monitor());
            if (!is(monitor, MONITOR)) continue;
            if (level.getRandom().nextDouble() < r.breakChance()) {
                BlockState broken = BuiltInRegistries.BLOCK.get(DAMAGED_MONITOR).defaultBlockState();
                if (broken.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    broken = broken.setValue(BlockStateProperties.HORIZONTAL_FACING, monitor.getValue(BlockStateProperties.HORIZONTAL_FACING));
                }
                level.setBlockAndUpdate(r.monitor(), broken);
                burnOut(level, r.monitor());
            } else {
                level.setBlockAndUpdate(r.monitor(), withScreen(monitor, MonitorBlock.MonitorScreen.OFF));
                level.playSound(null, r.monitor(), SoundEvent.createVariableRangeEvent(PC_OFF), SoundSource.BLOCKS, 1.5f, 0.5f);
            }
        }
    }

    private static MonitorBlock.MonitorScreen screenFor(String type) {
        try {
            return MonitorBlock.MonitorScreen.valueOf("TM_" + type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return READY_SCREEN;
        }
    }

    private static BlockState withScreen(BlockState monitor, MonitorBlock.MonitorScreen screen) {
        return monitor.hasProperty(MonitorBlock.Companion.getSCREEN())
                ? monitor.setValue(MonitorBlock.Companion.getSCREEN(), screen) : monitor;
    }

    private static @Nullable Vec3 landing(ServerLevel level, BlockPos spot, Direction behind, PokemonEntity entity, float yaw) {
        Direction side = behind.getClockWise();
        for (int back : new int[] {0, 1, -1, 2, 3}) {
            for (int across : new int[] {0, 2, -2, 4, -4}) {
                BlockPos at = spot.relative(behind, back).relative(side, across);
                Vec3 feet = WormholeDestinations.groundBelow(level, Vec3.atBottomCenterOf(at.above(MAX_RISE)));
                if (Math.abs(feet.y - spot.getY()) > MAX_RISE) continue;
                entity.moveTo(feet.x, feet.y, feet.z, yaw, 0f);
                AABB body = entity.getBoundingBox();
                if (level.getBlockCollisions(entity, body).iterator().hasNext() || level.containsAnyLiquid(body)) continue;
                if (PokemonArrivals.portalClear(level, feet.add(0, entity.getBbHeight() + 0.4, 0))) return feet;
            }
        }
        return null;
    }

    public static void onBroken(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || !is(state, ANALYZER)) return;
        Store store = Store.of(server);
        Loaded loaded = store.devices.remove(pos.asLong());
        if (loaded == null) return;
        store.setDirty();
        if (!loaded.core.isEmpty()) Block.popResource(level, pos, loaded.core.copy());
        if (!loaded.partner.isEmpty()) Block.popResource(level, pos, loaded.partner.copy());
        showContents(server, pos, null);
    }

    private static boolean is(BlockState state, ResourceLocation block) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(block);
    }

    private static String typeOf(Item gem) {
        String path = BuiltInRegistries.ITEM.getKey(gem).getPath();
        return path.endsWith("_gem") ? path.substring(0, path.length() - 4) : path;
    }

    private static final class Loaded {
        ItemStack core = ItemStack.EMPTY;
        ItemStack partner = ItemStack.EMPTY;
        boolean partnerLast;

        @Nullable Recipe recipe() {
            return core.isEmpty() || partner.isEmpty() ? null : recipeFor(core, partner);
        }
    }

    private static final class Store extends SavedData {
        private static final SavedData.Factory<Store> FACTORY = new SavedData.Factory<>(Store::new, Store::load, null);
        final Map<Long, Loaded> devices = new HashMap<>();
        final Map<Long, Long> overheated = new HashMap<>();
        final Map<Long, Long> errorUntil = new HashMap<>();

        static Store of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, "hyperspace_wormholes_cloning");
        }

        static Store load(CompoundTag tag, HolderLookup.Provider registries) {
            Store store = new Store();
            for (Tag t : tag.getList("Devices", Tag.TAG_COMPOUND)) {
                CompoundTag device = (CompoundTag) t;
                Loaded loaded = new Loaded();
                if (device.contains("Core")) loaded.core = ItemStack.parseOptional(registries, device.getCompound("Core"));
                if (device.contains("Partner")) loaded.partner = ItemStack.parseOptional(registries, device.getCompound("Partner"));
                if (device.contains("Gem")) {
                    ResourceLocation gem = ResourceLocation.tryParse(device.getString("Gem"));
                    if (gem != null) loaded.core = new ItemStack(BuiltInRegistries.ITEM.get(gem));
                }
                loaded.partnerLast = device.getBoolean("PartnerLast");
                store.devices.put(device.getLong("Pos"), loaded);
            }
            for (Tag t : tag.getList("Overheated", Tag.TAG_COMPOUND)) {
                CompoundTag hot = (CompoundTag) t;
                store.overheated.put(hot.getLong("Pos"), hot.getLong("Until"));
            }
            return store;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            devices.forEach((pos, loaded) -> {
                if (loaded.core.isEmpty() && loaded.partner.isEmpty()) return;
                CompoundTag device = new CompoundTag();
                device.putLong("Pos", pos);
                if (!loaded.core.isEmpty()) device.put("Core", loaded.core.save(registries));
                if (!loaded.partner.isEmpty()) device.put("Partner", loaded.partner.save(registries));
                device.putBoolean("PartnerLast", loaded.partnerLast);
                list.add(device);
            });
            tag.put("Devices", list);
            ListTag hot = new ListTag();
            overheated.forEach((pos, until) -> {
                CompoundTag device = new CompoundTag();
                device.putLong("Pos", pos);
                device.putLong("Until", until);
                hot.add(device);
            });
            tag.put("Overheated", hot);
            return tag;
        }
    }
}
