package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.block.campfirepot.SeasoningSlot;
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase;
import dev.hyperspacewormholes.hyperspace.DonutBoosts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Limits the cooking pot's seasoning slots for donuts: one of each berry, plus Legendary Essence.
 */
@Mixin(SeasoningSlot.class)
public class SeasoningSlotMixin {
    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$oneBerryAndEssence(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        SeasoningSlot self = (SeasoningSlot) (Object) this;
        RecipeHolder<CookingPotRecipeBase> recipe = self.getMenu().getCurrentActiveRecipe();
        if (recipe == null) return;
        ResourceLocation tag = recipe.value().getSeasoningTag().location();
        if (!tag.equals(DonutBoosts.DONUT_SEASONINGS) && !tag.equals(DonutBoosts.DONUT_BERRIES)) return;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean berry = DonutBoosts.baseBerry(id) != null;
        if (!berry && !id.equals(DonutBoosts.ESSENCE)) return;
        for (Slot slot : self.getMenu().slots) {
            if (slot == self || !(slot instanceof SeasoningSlot) || slot.getItem().isEmpty()) continue;
            String other = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();
            if (berry ? DonutBoosts.baseBerry(other) != null && !other.equals(id) : other.equals(id)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
