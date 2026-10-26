package com.cookiecraftmods.eqlib.mixin;

import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class DecorationPlacementItemMixin {
    @Shadow protected abstract boolean canPlace(BlockItemUseContext context, BlockState state);
    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
    private void mdm$preciseState(BlockItemUseContext context, CallbackInfoReturnable<BlockState> callback) {
        if (!PrecisePlacement.requested(context)) return;
        var plan = PrecisePlacement.plan(context);
        if (plan.valid()) {
            PrecisePlacement.begin(plan);
            if (canPlace(context, plan.state())) {
                callback.setReturnValue(plan.state());
                return;
            }
        }
        PrecisePlacement.end();
        callback.setReturnValue(null);
    }

    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void mdm$storeOffset(BlockItemUseContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) PrecisePlacement.commit(context.getLevel(), context.getClickedPos());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void mdm$finish(CallbackInfoReturnable<net.minecraft.util.ActionResultType> callback) {
        PrecisePlacement.end();
    }
}
