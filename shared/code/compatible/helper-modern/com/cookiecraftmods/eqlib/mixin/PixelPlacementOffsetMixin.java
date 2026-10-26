package com.cookiecraftmods.eqlib.mixin;

import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PixelPlacementOffsetMixin {
    @Shadow protected abstract BlockState asState();
    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void mdm$offset(BlockPos pos, CallbackInfoReturnable<Vec3> callback) {
        BlockState state = asState();
        if (!PrecisePlacement.supported(state.getBlock())) return;
        Vec3 offset = PrecisePlacement.isPlanning() ? Vec3.ZERO : PrecisePlacement.clientOffset.apply(pos);
        offset = offset.add(com.cookiecraftmods.eqlib.api.FurnitureApi.modelOffset(state));
        callback.setReturnValue(offset);
    }
    @Inject(method = "hasOffsetFunction", at = @At("HEAD"), cancellable = true)
    private void mdm$hasOffset(CallbackInfoReturnable<Boolean> callback) {
        if (PrecisePlacement.supported(asState().getBlock())) callback.setReturnValue(true);
    }
}
