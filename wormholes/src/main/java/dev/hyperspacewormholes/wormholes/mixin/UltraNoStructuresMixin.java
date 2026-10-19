package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps structures out of the Ultra Beast worlds. They're only tagged ocean and jungle so Cobblemon spawns the right
 * things.
 */
@Mixin(ChunkGenerator.class)
public abstract class UltraNoStructuresMixin {
    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
                                                StructureManager structureManager, ChunkAccess chunk,
                                                StructureTemplateManager templateManager, CallbackInfo ci) {
        if ((Object) this instanceof NoiseBasedChunkGenerator noise
                && noise.generatorSettings().unwrapKey().map(key -> key.location().getNamespace().equals(Wormholes.MOD_ID)).orElse(false)) {
            ci.cancel();
        }
    }
}
