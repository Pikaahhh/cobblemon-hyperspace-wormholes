package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

/**
 * Walks a copy of a section's entities when a chunk's status changes, skipping any removed along the way. The game starts
 * and stops ticking and tracking each entity while walking the live list, and those steps call out to mods (Fabric's
 * entity load and unload events, among them this mod's clean-ups that remove a stale Pokemon), so an entity removed,
 * added or moved there threw a ConcurrentModificationException.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntitySectionSnapshotMixin {
    @WrapOperation(method = "method_31825",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntitySection;getEntities()Ljava/util/stream/Stream;"))
    private Stream<EntityAccess> hyperspace_wormholes$snapshot(EntitySection<EntityAccess> section,
                                                               Operation<Stream<EntityAccess>> original) {
        return original.call(section).toList().stream().filter(e -> !(e instanceof Entity entity && entity.isRemoved()));
    }
}
