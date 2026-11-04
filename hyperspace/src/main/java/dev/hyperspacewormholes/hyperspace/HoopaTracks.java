package dev.hyperspacewormholes.hyperspace;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays Hoopa's flying and resizing rings on the client. A server sending a pose every couple of ticks stutters as
 * the packets arrive unevenly, so HoopaSummon sends all of a ring's poses at once (Track) and each is set here on its
 * own client tick, the game easing between them.
 */
public final class HoopaTracks {
    private static final int WAIT = 40;
    private static final List<Playing> playing = new ArrayList<>();

    private HoopaTracks() {
    }

    private static final class Playing {
        final int entity;
        final float[] poses;
        int at, waited;

        Playing(int entity, float[] poses) {
            this.entity = entity;
            this.poses = poses;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HoopaSummon.Track.TYPE, (payload, context) -> playing.add(new Playing(payload.entity(), payload.poses())));
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.level == null) {
                playing.clear();
                return;
            }
            if (mc.isPaused()) return;
            for (Iterator<Playing> it = playing.iterator(); it.hasNext(); ) {
                Playing p = it.next();
                Entity entity = mc.level.getEntity(p.entity);
                if (!(entity instanceof Display display)) {
                    // the payload can get here before the ring does
                    if (++p.waited > WAIT || p.at > 0) it.remove();
                    continue;
                }
                float[] f = p.poses;
                int i = p.at;
                HoopaFeed.pose(display, new Transformation(new Vector3f(f[i], f[i + 1], f[i + 2]), new Quaternionf(f[i + 3], f[i + 4], f[i + 5], f[i + 6]),
                        new Vector3f(f[i + 7], f[i + 7], f[i + 7]), new Quaternionf()), 1);
                p.at += HoopaSummon.POSE;
                if (p.at >= f.length) it.remove();
            }
        });
    }
}
