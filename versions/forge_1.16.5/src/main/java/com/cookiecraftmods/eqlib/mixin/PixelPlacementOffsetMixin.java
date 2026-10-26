package com.cookiecraftmods.eqlib.mixin;

import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.vector.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class PixelPlacementOffsetMixin {
    @Shadow protected abstract BlockState asState();
    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void mdm$offset(IBlockReader level, BlockPos pos, CallbackInfoReturnable<Vector3d> callback) {
        BlockState state = asState();
        if (!PrecisePlacement.supported(state.getBlock())) return;
        Vector3d offset = PrecisePlacement.offset(level, pos);
        offset = offset.add(com.cookiecraftmods.eqlib.api.FurnitureApi.modelOffset(state));
        callback.setReturnValue(offset);
    }
}
