package com.cookiecraftmods.eqlib.mixin;
import com.cookiecraftmods.eqlib.placement.PlacementOffsetAccess;
import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.nbt.CompoundNBT;

@Mixin(TileEntity.class)
public abstract class PlacementPersistenceMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private void mdm$read(net.minecraft.block.BlockState state, CompoundNBT tag, CallbackInfo callback) {
        var data = (PlacementOffsetAccess) this;
        data.mdm$setPlacementOffset(tag.getBoolean("mdm_precise") ? new Vector3d(tag.getDouble("mdm_offset_x"), tag.getDouble("mdm_offset_y"), tag.getDouble("mdm_offset_z")) : null);
        if (data.mdm$getPlacementOffset() != null) PrecisePlacement.refresh((TileEntity)(Object)this);
    }
    @Inject(method = "save", at = @At("RETURN"))
    private void mdm$write(CompoundNBT tag, CallbackInfoReturnable<CompoundNBT> callback) {
        Vector3d offset = ((PlacementOffsetAccess) this).mdm$getPlacementOffset();
        if (offset == null) return;
        tag.putBoolean("mdm_precise", true);
        tag.putDouble("mdm_offset_x", offset.x);
        tag.putDouble("mdm_offset_y", offset.y);
        tag.putDouble("mdm_offset_z", offset.z);
    }
    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
    private void mdm$syncTag(CallbackInfoReturnable<CompoundNBT> callback) {
        if (((PlacementOffsetAccess) this).mdm$getPlacementOffset() != null) {
            callback.setReturnValue(((TileEntity)(Object)this).save(new CompoundNBT()));
        }
    }
    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void mdm$syncPacket(CallbackInfoReturnable<SUpdateTileEntityPacket> callback) {
        if (((PlacementOffsetAccess) this).mdm$getPlacementOffset() != null) {
            callback.setReturnValue(new SUpdateTileEntityPacket(((TileEntity)(Object)this).getBlockPos(), 0, ((TileEntity)(Object)this).getUpdateTag()));
        }
    }
}
