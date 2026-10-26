package com.cookiecraftmods.eqlib.mixin;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Entity-backed placement cannot use the shape cache shared by every occurrence of a block state.
@Mixin(AbstractBlock.class)
public abstract class DecorationDynamicShapeMixin {
    @Shadow @Final @Mutable protected boolean dynamicShape;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mdm$dynamicPlacement(CallbackInfo callback) {
        if (com.cookiecraftmods.eqlib.api.FurnitureApi.supportsClass(getClass())) dynamicShape = true;
    }
    @Inject(method = "getOffsetType", at = @At("HEAD"), cancellable = true)
    private void mdm$hasOffset(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<AbstractBlock.OffsetType> callback) {
        if (com.cookiecraftmods.eqlib.api.FurnitureApi.supportsClass(getClass())) callback.setReturnValue(AbstractBlock.OffsetType.XYZ);
    }
}
