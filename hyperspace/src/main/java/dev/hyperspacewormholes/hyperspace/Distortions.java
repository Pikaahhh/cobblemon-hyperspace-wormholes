package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Spatial distortions, the Legends Z-A way into Hyperspace. One or two hang around every player in the Overworld, 64 to 160 blocks away and always flat against a wall (a cliff, hillside or building face at least 3x3 with a clear 3x3 space in front), each with a 1 to 5 star rank and one to three types from its biome, lasting 60 minutes while new ones form.
 * A Rift is drawn flat on its wall in pixel art, with its name, stars and types as a label that RiftRenderer draws just above it; stir() makes it throb while Hoopa charges up.
 * Feeding Hoopa a donut there opens a portal to a Hyperspace of that rank and types, the donut's calories buying the time; a Distortion Splicer splices it open instead (see DistortionSplicer). Saved with the world; nothing is placed in the terrain.
 */
public class Distortions {
    public static final int REACH = 8;
    private static final int FEWER = 1, MORE = 2, TAKES_TURNS = 8;
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 160;
    private static final int AROUND = 192;
    private static final int SPACING = 48;
    private static final int KEEP_CLEAR = 32;
    private static final int TRIES = 8;
    private static final int WALL_SEARCH = 16;
    private static final int WALL_HEIGHT = 3;
    private static final double PLACE_REACH = 6;
    private static final double WALL_GAP = 0.3;
    private static final long LIFETIME = 60 * 60 * 20;
    private static final int FADE = 100;
    private static final int[] STAR_WEIGHTS = {35, 28, 20, 12, 5};
    private static final int[] TYPE_COUNT_WEIGHTS = {40, 40, 20};
    private static final double RIFT_HEIGHT = 1.5;
    private static final String TAG = "hyperspace_wormholes_distortion";

    private record Wall(BlockPos spot, Direction facing) {
    }

    public static class Distortion {
        int x, y, z, stars;
        Direction facing;
        long ends;
        List<String> types = new ArrayList<>();
        UUID rift, label;
        boolean fading, hoopa;
        String legendary, mega;

        Vec3 base() {
            return new Vec3(x + 0.5, y, z + 0.5).add(normal().scale(WALL_GAP - 0.5));
        }

        Vec3 normal() {
            return new Vec3(facing.getStepX(), 0, facing.getStepZ());
        }

        double distance(Entity entity) {
            return Math.sqrt(Mth.square(entity.getX() - x - 0.5) + Mth.square(entity.getZ() - z - 0.5));
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", x);
            t.putInt("y", y);
            t.putInt("z", z);
            t.putInt("stars", stars);
            if (facing != null) t.putString("facing", facing.getSerializedName());
            t.putLong("ends", ends);
            ListTag list = new ListTag();
            for (String type : types) list.add(StringTag.valueOf(type));
            t.put("types", list);
            if (rift != null) t.putUUID("rift", rift);
            if (hoopa) t.putBoolean("hoopa", true);
            if (legendary != null) t.putString("legendary", legendary);
            if (mega != null) t.putString("mega", mega);
            return t;
        }

        static Distortion load(CompoundTag t) {
            Distortion distortion = new Distortion();
            distortion.x = t.getInt("x");
            distortion.y = t.getInt("y");
            distortion.z = t.getInt("z");
            distortion.stars = t.contains("stars") ? t.getInt("stars") : 1;
            distortion.facing = Direction.byName(t.getString("facing"));
            distortion.ends = t.getLong("ends");
            ListTag list = t.getList("types", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) distortion.types.add(list.getString(i));
            distortion.rift = t.hasUUID("rift") ? t.getUUID("rift") : null;
            distortion.label = t.hasUUID("label") ? t.getUUID("label") : null;
            distortion.hoopa = t.getBoolean("hoopa");
            distortion.legendary = t.contains("legendary") ? t.getString("legendary") : null;
            distortion.mega = t.contains("mega") ? t.getString("mega") : null;
            return distortion;
        }
    }

    public static void register() {
        Rift.register();
        ServerTickEvents.END_SERVER_TICK.register(Distortions::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG)) entity.discard();
        });
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldData data = WorldData.get(server);
        long now = level.getGameTime();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) if (!player.isSpectator()) players.add(player);

        Iterator<Distortion> it = data.distortions.iterator();
        while (it.hasNext()) {
            Distortion distortion = it.next();
            long left = distortion.ends - now;
            if (left <= 0 || distortion.facing == null || !distortion.facing.getAxis().isHorizontal()) {
                hide(level, distortion);
                it.remove();
                data.setDirty();
                continue;
            }
            if (!watched(distortion, players)) continue;
            if (left <= FADE && !distortion.fading) fade(level, distortion);
            if (server.getTickCount() % 100 == 0 && show(level, distortion)) data.setDirty();
        }

        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : players) refill(level, data, player, players);
        }
    }

    private static boolean watched(Distortion distortion, List<ServerPlayer> players) {
        for (ServerPlayer player : players) if (distortion.distance(player) < AROUND) return true;
        return false;
    }

    /** How many distortions this player wants around: one or two, by a checkerboard of the map. */
    private static int wanted(ServerPlayer player) {
        return ((player.getBlockX() >> TAKES_TURNS) + (player.getBlockZ() >> TAKES_TURNS) & 1) == 0 ? FEWER : MORE;
    }

    private static void refill(ServerLevel level, WorldData data, ServerPlayer player, List<ServerPlayer> players) {
        int around = 0;
        for (Distortion distortion : data.distortions) if (distortion.distance(player) < AROUND) around++;
        if (around >= wanted(player)) return;
        RandomSource random = level.getRandom();
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            Wall wall = findWall(level, Mth.floor(player.getX() + Math.cos(angle) * distance),
                    Mth.floor(player.getZ() + Math.sin(angle) * distance), random);
            if (wall == null || crowded(data, wall.spot(), players)) continue;
            add(level, wall, rollStars(random), rollTypes(level, wall.spot(), random));
            return;
        }
    }

    private static Wall findWall(ServerLevel level, int x, int z, RandomSource random) {
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Util.shuffle(sides, random);
        for (Direction facing : sides) {
            int wx = x, wz = z;
            Integer ground = surface(level, wx, wz);
            for (int step = 0; ground != null && step < WALL_SEARCH; step++) {
                int nx = wx - facing.getStepX(), nz = wz - facing.getStepZ();
                Integer next = surface(level, nx, nz);
                if (next != null && next >= ground + WALL_HEIGHT) {
                    BlockPos spot = new BlockPos(wx, ground, wz);
                    if (level.isPositionEntityTicking(spot) && isWall(level, spot, facing)) return new Wall(spot, facing);
                    break;
                }
                wx = nx;
                wz = nz;
                ground = next;
            }
        }
        return null;
    }

    private static Integer surface(ServerLevel level, int x, int z) {
        return level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : null;
    }

    private static boolean isWall(ServerLevel level, BlockPos spot, Direction facing) {
        BlockPos below = spot.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.getFluidState().isEmpty() || floor.is(BlockTags.LEAVES) || !floor.isFaceSturdy(level, below, Direction.UP)) return false;
        Direction side = facing.getClockWise();
        for (int across = -1; across <= 1; across++) {
            for (int up = 0; up < WALL_HEIGHT; up++) {
                BlockPos back = spot.relative(side, across).above(up).relative(facing.getOpposite());
                BlockState block = level.getBlockState(back);
                if (block.is(BlockTags.LEAVES) || !block.isFaceSturdy(level, back, facing)) return false;
            }
        }
        AABB room = AABB.encapsulatingFullBlocks(spot.relative(side, -1),
                spot.relative(side, 1).relative(facing).above(WALL_HEIGHT - 1)).deflate(1.0E-3);
        return level.noCollision(room) && !level.containsAnyLiquid(room);
    }

    private static boolean crowded(WorldData data, BlockPos spot, List<ServerPlayer> players) {
        for (Distortion other : data.distortions) {
            if (Mth.square(other.x - spot.getX()) + Mth.square(other.z - spot.getZ()) < SPACING * SPACING) return true;
        }
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(Vec3.atBottomCenterOf(spot)) < KEEP_CLEAR * KEEP_CLEAR) return true;
        }
        return false;
    }

    private static int rollStars(RandomSource random) {
        return pick(random, STAR_WEIGHTS) + 1;
    }

    private static List<String> rollTypes(ServerLevel level, BlockPos spot, RandomSource random) {
        List<String> biome = new ArrayList<>(Zones.types(level.getBiome(spot)));
        Util.shuffle(biome, random);
        int count = pick(random, TYPE_COUNT_WEIGHTS) + 1;
        List<String> types = new ArrayList<>();
        for (String type : biome) if (types.size() < count && DonutBoosts.TYPES.contains(type)) types.add(type);
        while (types.size() < count) {
            String type = DonutBoosts.TYPES.get(random.nextInt(DonutBoosts.TYPES.size()));
            if (!types.contains(type)) types.add(type);
        }
        return types;
    }

    private static int pick(RandomSource random, int[] weights) {
        int total = 0;
        for (int weight : weights) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }

    private static Distortion add(ServerLevel level, Wall wall, int stars, List<String> types) {
        Distortion distortion = new Distortion();
        distortion.x = wall.spot().getX();
        distortion.y = wall.spot().getY();
        distortion.z = wall.spot().getZ();
        distortion.facing = wall.facing();
        distortion.stars = stars;
        distortion.types = new ArrayList<>(types);
        distortion.ends = level.getGameTime() + LIFETIME;
        WorldData data = WorldData.get(level.getServer());
        data.distortions.add(distortion);
        show(level, distortion);
        data.setDirty();
        return distortion;
    }

    /** Where you're looking, with a Pokemon or the Hoopa event waiting behind it if asked: /hyperspace createDistortion. */
    public static Distortion place(ServerPlayer player, int stars, List<String> types, String legendary, String mega, boolean hoopa) {
        ServerLevel level = player.serverLevel();
        if (!(player.pick(PLACE_REACH, 1f, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getDirection().getAxis().isHorizontal()) {
            return null;
        }
        Direction facing = hit.getDirection();
        BlockPos spot = hit.getBlockPos().relative(facing);
        for (int i = 0; i < 4 && !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP); i++) spot = spot.below();
        if (!isWall(level, spot, facing)) return null;
        Wall wall = new Wall(spot, facing);
        WorldData data = WorldData.get(level.getServer());
        data.distortions.removeIf(other -> {
            if (Mth.square(other.x - wall.spot().getX()) + Mth.square(other.z - wall.spot().getZ()) >= 16) return false;
            hide(level, other);
            return true;
        });
        RandomSource random = level.getRandom();
        Distortion placed = add(level, wall, stars > 0 ? stars : rollStars(random), types.isEmpty() ? rollTypes(level, wall.spot(), random) : types);
        placed.legendary = legendary;
        placed.mega = mega;
        placed.hoopa = hoopa;
        return placed;
    }

    public static List<Distortion> all(MinecraftServer server) {
        return WorldData.get(server).distortions;
    }

    /** Takes away every distortion within this many blocks of here, and says how many went. */
    public static int clear(ServerLevel level, Vec3 from, int radius) {
        WorldData data = WorldData.get(level.getServer());
        int before = data.distortions.size();
        data.distortions.removeIf(distortion -> {
            if (Mth.square(distortion.x + 0.5 - from.x) + Mth.square(distortion.z + 0.5 - from.z) > (double) radius * radius) return false;
            hide(level, distortion);
            return true;
        });
        data.setDirty();
        return before - data.distortions.size();
    }

    private static boolean show(ServerLevel level, Distortion distortion) {
        if (distortion.fading || !level.isPositionEntityTicking(BlockPos.containing(distortion.base()))) return false;
        boolean changed = dropLabel(level, distortion);
        if (distortion.rift == null || !(level.getEntity(distortion.rift) instanceof Rift)) {
            distortion.rift = Rift.distortion(level, distortion.base(), distortion.normal(), distortion.ends - LIFETIME,
                    Component.translatable("distortion.hyperspace_wormholes.label", stars(distortion.stars))
                            .withStyle(ChatFormatting.LIGHT_PURPLE).append("\n").append(typeList(distortion.types))).getUUID();
            changed = true;
        }
        return changed;
    }

    private static boolean dropLabel(ServerLevel level, Distortion distortion) {
        if (distortion.label == null) return false;
        Entity label = level.getEntity(distortion.label);
        if (label != null) label.discard();
        distortion.label = null;
        return true;
    }

    private static void fade(ServerLevel level, Distortion distortion) {
        distortion.fading = true;
        if (distortion.rift != null && level.getEntity(distortion.rift) instanceof Rift rift) rift.end();
        dropLabel(level, distortion);
    }

    private static void hide(ServerLevel level, Distortion distortion) {
        Entity rift = distortion.rift == null ? null : level.getEntity(distortion.rift);
        if (rift != null) rift.discard();
        dropLabel(level, distortion);
    }

    public static void stir(ServerLevel level, Distortion distortion) {
        if (distortion.rift != null && level.getEntity(distortion.rift) instanceof Rift rift) rift.stir();
    }

    public static Distortion near(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return null;
        Distortion best = null;
        double bestDistance = REACH;
        for (Distortion distortion : WorldData.get(player.server).distortions) {
            double distance = distortion.distance(player);
            if (distortion.fading || distortion.facing == null || distance > bestDistance || Math.abs(player.getY() - distortion.y) > 6) continue;
            best = distortion;
            bestDistance = distance;
        }
        return best;
    }

    static Distortion nearest(ServerPlayer player) {
        Distortion best = null;
        for (Distortion distortion : WorldData.get(player.server).distortions) {
            if (!distortion.fading && distortion.facing != null && (best == null || distortion.distance(player) < best.distance(player))) best = distortion;
        }
        return best;
    }

    public static boolean open(ServerPlayer player, Distortion distortion, ItemStack stack) {
        if (distortion.hoopa) return splice(player, distortion, true);
        ServerLevel level = player.serverLevel();
        if (!Trips.ready(level.getServer())) return false;
        Vec3 base = distortion.base();
        Vec3 normal = distortion.normal();
        if (!Portals.grounded(level, base)) return fail(player, "ground");
        if (!Portals.fits(level, base, normal)) return fail(player, "room");
        int seconds = DonutBoosts.seconds(stack, distortion.stars);
        if (!Portals.open(player, stack, base, normal, distortion.stars, distortion.types, seconds, distortion.legendary, distortion.mega)) {
            return fail(player, "full");
        }

        used(level, distortion);
        return true;
    }

    public static boolean splice(ServerPlayer player, Distortion distortion, boolean forceHoopa) {
        ServerLevel level = player.serverLevel();
        if (!Trips.ready(level.getServer())) return false;
        Vec3 base = distortion.base();
        Vec3 normal = distortion.normal();
        if (!Portals.grounded(level, base)) return fail(player, "ground");
        if (!Portals.fits(level, base, normal)) return fail(player, "room");
        boolean hoopa = distortion.legendary == null && distortion.mega == null && DistortionSplicer.rollHoopa(player, forceHoopa || distortion.hoopa);
        int seconds = DistortionSplicer.seconds(player.getRandom());
        if (!Portals.splice(player, base, normal, distortion.stars, distortion.types, seconds, hoopa, distortion.legendary, distortion.mega)) {
            return fail(player, "full");
        }

        DistortionSplicer.counted(player, hoopa);
        used(level, distortion);
        level.playSound(null, base.x, base.y + RIFT_HEIGHT, base.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 0.6f);
        return true;
    }

    private static void used(ServerLevel level, Distortion distortion) {
        WorldData data = WorldData.get(level.getServer());
        hide(level, distortion);
        data.distortions.remove(distortion);
        data.setDirty();
        Vec3 base = distortion.base();
        level.playSound(null, base.x, base.y + RIFT_HEIGHT, base.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.8f);
    }

    private static boolean fail(ServerPlayer player, String why) {
        player.sendSystemMessage(Component.translatable("distortion.hyperspace_wormholes." + why).withStyle(ChatFormatting.RED));
        return false;
    }

    /** The only message a refused Hoopa or splicer gives, whatever is out there, so the player is never told where a distortion is. */
    public static void noneNearby(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("distortion.hyperspace_wormholes.none_nearby").withStyle(ChatFormatting.RED));
    }

    static Component stars(int stars) {
        return Component.literal(DonutBoosts.starText(stars)).withStyle(ChatFormatting.GOLD);
    }

    static MutableComponent typeList(List<String> types) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) line.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY));
            line.append(DonutBoosts.typeName(types.get(i)));
        }
        return line;
    }

    static String time(int seconds) {
        return seconds / 60 + "m " + String.format("%02d", seconds % 60) + "s";
    }

    public static String describe(Distortion distortion, ServerPlayer player, long now) {
        long minutes = (distortion.ends - now) / 1200;
        return DonutBoosts.starText(distortion.stars) + " " + String.join(" / ", distortion.types) + " at " + distortion.x + " "
                + distortion.y + " " + distortion.z + (player == null ? "" : ", " + Math.round(distortion.distance(player)) + " blocks away")
                + ", " + minutes + "m left" + (distortion.legendary != null ? ", legendary " + distortion.legendary : "")
                + (distortion.mega != null ? ", Mega " + distortion.mega : "") + (distortion.hoopa ? ", Hoopa Unbound" : "");
    }
}
