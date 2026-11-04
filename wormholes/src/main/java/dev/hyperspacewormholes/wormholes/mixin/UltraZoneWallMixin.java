package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The edge of an Ultra Space Wilds zone (see Zone) as an invisible wall: a player, or what a player rides, can't move
 * out of the zone's walk mask or up past its ceiling. Whatever part of a move would cross is dropped after the block
 * collisions, so they stop at the edge and slide along it. Runs on the client and the server; someone already outside
 * (a teleport) moves freely and UltraZones brings them back.
 */
@Mixin(Entity.class)
public abstract class UltraZoneWallMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$zoneWall(Vec3 wanted, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        Zone zone = UltraZones.of(self.level());
        if (zone == null || !UltraZones.walled(self)) return;
        Vec3 pos = self.position(), move = cir.getReturnValue();
        if (!zone.allows(pos.x, pos.y, pos.z) || zone.allows(pos.x + move.x, pos.y + move.y, pos.z + move.z)) return;
        double dy = move.y > 0 ? Math.min(move.y, Math.max(0, zone.ceiling() - pos.y)) : move.y;
        double y = pos.y + dy;
        double dx = zone.allows(pos.x + move.x, y, pos.z) ? move.x : 0;
        double dz = zone.allows(pos.x, y, pos.z + move.z) ? move.z : 0;
        if (!zone.allows(pos.x + dx, y, pos.z + dz)) dz = 0;
        cir.setReturnValue(new Vec3(dx, dy, dz));
    }
}
