package com.cookiecraftmods.eqlib.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.LivingEntity;
import com.cookiecraftmods.eqlib.block.SeatBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SeatLifecycleMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void eqlib$cleanupSeat(CallbackInfo callback) {
        if (!((Object)this instanceof ArmorStand)) return;
        ArmorStand seat = (ArmorStand)(Object)this;
        SeatBlock.tickSeat(seat.level, seat);
    }
}
