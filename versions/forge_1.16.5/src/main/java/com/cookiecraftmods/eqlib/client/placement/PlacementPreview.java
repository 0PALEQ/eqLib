package com.cookiecraftmods.eqlib.client.placement;
import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Hand;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.math.BlockRayTraceResult;

public final class PlacementPreview {
    private PlacementPreview() {}
    public static PrecisePlacement.Plan current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isSpectator()
                || !mc.player.isShiftKeyDown() || !(mc.hitResult instanceof BlockRayTraceResult hit) || hit.getType() != net.minecraft.util.math.RayTraceResult.Type.BLOCK) return null;
        for (Hand hand : Hand.values()) {
            var stack = mc.player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem item) || !PrecisePlacement.supported(item.getBlock())) continue;
            var context = new BlockItemUseContext(mc.player, hand, stack, hit);
            return PrecisePlacement.plan(context);
        }
        return null;
    }
}
