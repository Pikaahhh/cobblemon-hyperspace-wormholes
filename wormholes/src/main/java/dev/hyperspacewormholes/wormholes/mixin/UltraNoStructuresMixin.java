package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Keeps other structures out of the Ultra Beast worlds, leaving only the mod's own zones (ZoneStructure). They're only
 * tagged ocean and jungle so Cobblemon spawns the right things. The flat worlds do this with structure_overrides in
 * their dimension files; a noise generator has no such field.
 */
@Mixin(ChunkGenerator.class)
public abstract class UltraNoStructuresMixin {
    @Redirect(method = "createStructures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
    private List<Holder<StructureSet>> hyperspace_wormholes$onlyZones(ChunkGeneratorStructureState state) {
        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        if (!((Object) this instanceof NoiseBasedChunkGenerator noise
                && noise.generatorSettings().unwrapKey().map(key -> key.location().getNamespace().equals(Wormholes.MOD_ID)).orElse(false))) {
            return sets;
        }
        return sets.stream().filter(set -> set.unwrapKey().map(key -> key.location().getNamespace().equals(Wormholes.MOD_ID)).orElse(false)).toList();
    }
}
