package com.cookiecraftmods.eqlib.mixin;

import com.cookiecraftmods.eqlib.block.FurnitureFootprint;
import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class DecorationPlacementShapeMixin {
    @Shadow protected abstract BlockState asState();

    @Inject(method = "getShape(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/shapes/ISelectionContext;)Lnet/minecraft/util/math/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void mdm$selection(IBlockReader level, BlockPos pos, ISelectionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        if (PrecisePlacement.supported(asState().getBlock()) && PrecisePlacement.active(level, pos)) {
            callback.setReturnValue(FurnitureFootprint.localShape(PrecisePlacement.fullShape(asState(), level, pos, false), BlockPos.ZERO));
        }
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/shapes/ISelectionContext;)Lnet/minecraft/util/math/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void mdm$collision(IBlockReader level, BlockPos pos, ISelectionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        if (PrecisePlacement.supported(asState().getBlock()) && PrecisePlacement.active(level, pos)) {
            callback.setReturnValue(FurnitureFootprint.localShape(PrecisePlacement.fullShape(asState(), level, pos, true), BlockPos.ZERO));
        }
    }
}
