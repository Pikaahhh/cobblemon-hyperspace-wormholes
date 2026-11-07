package dev.hyperspacewormholes.wormholes.compat;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

import java.lang.reflect.Method;

/**
 * Jade plugin (loaded only when Jade is installed) that hides the tooltip of blocks players aren't meant to have: every
 * block of ours with no item, which are the ones only world generation and /setblock place. Jade's API is compiled
 * against stand-ins (src/jadeApi) and hideTarget is called by reflection, so building or running needs no Jade.
 */
public final class JadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        try {
            Method hide = registration.getClass().getMethod("hideTarget", Block.class);
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Wormholes.MOD_ID) || block.asItem() != Items.AIR) continue;
                hide.invoke(registration, block);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.warn("Couldn't hide the Ultra worlds' own blocks from Jade", e);
        }
    }
}
