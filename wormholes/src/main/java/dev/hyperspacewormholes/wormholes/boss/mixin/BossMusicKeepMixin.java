package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMusicHandler;
import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps one of this mod's boss themes playing on when Cobblemon asks for that same theme again, instead of starting it over: the Megalopolis Necrozma's theme starts with its opening transition, and the battles that begin on the transition's black frame ask for it once more. */
@Mixin(value = BattleMusicHandler.class, remap = false)
public abstract class BossMusicKeepMixin {
    // the bare name is the packet-typed handle, first in the class; a descriptor would name Minecraft, another class in production
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepBossTheme(BattleMusicPacket packet, Minecraft client, CallbackInfo ci) {
        ResourceLocation music = packet.getMusic();
        if (music == null || !music.getNamespace().equals(Wormholes.MOD_ID)) return;
        BattleMusicInstance current = BattleMusicController.INSTANCE.getMusic();
        if (music.equals(current.getLocation()) && !current.isFading() && client.getSoundManager().isActive(current)) ci.cancel();
    }
}
