package dev.hyperspacewormholes.hyperspace;

import net.minecraft.client.KeyMapping;

/**
 * The four quick battle move keys once QuickBattleTargeting has registered them (empty before), kept apart from it so a
 * mixin can look at them without loading QuickBattleTargeting early.
 */
public final class MoveKeys {
    public static KeyMapping[] keys = new KeyMapping[0];

    private MoveKeys() {}

    public static boolean is(KeyMapping mapping) {
        for (KeyMapping key : keys) if (key == mapping) return true;
        return false;
    }
}
