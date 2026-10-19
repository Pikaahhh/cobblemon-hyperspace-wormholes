package dev.hyperspacewormholes.wormholes.ride;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where you come back out when you leave Ultra Space. Saved on the player.
 */
public record HomePoint(ResourceKey<Level> dimension, Vec3 pos, float yRot) {
    public static final Codec<HomePoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(HomePoint::dimension),
            Vec3.CODEC.fieldOf("pos").forGetter(HomePoint::pos),
            Codec.FLOAT.fieldOf("y_rot").forGetter(HomePoint::yRot)
    ).apply(i, HomePoint::new));

    static HomePoint worldSpawn(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return new HomePoint(overworld.dimension(), Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), 0f);
    }
}
