package com.cookiecraftmods.eqlib.tests;

import com.cookiecraftmods.eqlib.api.FurnitureApi;
import com.cookiecraftmods.eqlib.block.FurnitureFootprint;
import com.cookiecraftmods.eqlib.block.SeatBlock;
import com.cookiecraftmods.eqlib.placement.PlacementOffsetAccess;
import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.UUID;

/** Runs against an external universal JAR, not a source variant compiled for this game. */
public final class UniversalServerTest implements ModInitializer {
    private static boolean verified;
    private static int placements;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            try {
                require(verified, "Compatibility checks did not complete");
                String result="UNIVERSAL_SERVER_PASSED placements="+placements+" saved-offsets=checked seats=invulnerable+cleanup shutdown=clean";
                Files.writeString(Path.of("universal-server-result.txt"),result);
                System.out.println(result);
            } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(1); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var level=server.overworld();
                var profile=new GameProfile(UUID.randomUUID(),"UniversalTest");
                var player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
                player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,
                        CommonListenerCookie.createInitial(profile,false));
                player.setPos(0.5,200,5.5);
                var pos=new BlockPos(0,200,0);
                level.getChunkAt(pos);
                var blocks=BuiltInRegistries.BLOCK.stream().filter(PrecisePlacement::supported).toList();
                require(blocks.size()>300,"Consumer registry did not load");
                player.setShiftKeyDown(true);
                for (Block block:blocks) {
                    for (Direction face:Direction.values()) {
                        for (BlockPos cell:BlockPos.betweenClosed(pos.offset(-4,-4,-4),pos.offset(4,4,4))) level.removeBlock(cell,false);
                        var hit=new BlockHitResult(new Vec3(0.70,200.60,0.20),face,pos,false);
                        var stack=new ItemStack(block.asItem(),2);
                        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                        var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
                        var plan=PrecisePlacement.plan(context);
                        if (!plan.valid()) continue;
                        var result=((BlockItem)stack.getItem()).place(context);
                        require(result.consumesAction(),"Rejected valid plan: "+block);
                        var entity=level.getBlockEntity(plan.pos());
                        require(entity instanceof PlacementOffsetAccess,"Missing offset interface: "+block);
                        require(plan.offset().equals(((PlacementOffsetAccess)entity).mdm$getPlacementOffset()),"Offset mismatch: "+block);
                        var tag=entity.saveWithoutMetadata(level.registryAccess());
                        require(tag.getBooleanOr("mdm_precise",false),"Offset not saved: "+block);
                        require(entity.getUpdatePacket()!=null,"No offset update packet: "+block);
                        require(PrecisePlacement.fullShape(plan.state(),level,plan.pos(),false)!=null,"Missing shifted shape");
                        FurnitureFootprint.removeParts(level,plan.pos(),plan.state());
                        level.removeBlock(plan.pos(),false);
                        placements++;
                    }
                }
                require(placements>1000,"Too few real placement paths exercised: "+placements);
                player.setShiftKeyDown(false);
                var chair=blocks.stream().filter(b -> FurnitureApi.sittable(b.defaultBlockState())).findFirst().orElseThrow();
                level.setBlockAndUpdate(pos,chair.defaultBlockState());
                SeatBlock.setSeatHeightPixels(chair,10);
                require(SeatBlock.use(chair.defaultBlockState(),level,pos,player).consumesAction(),"Seat interaction failed");
                require(player.getVehicle() instanceof ArmorStand,"Seat mount not created");
                var seat=(ArmorStand)player.getVehicle();
                String getter=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                        .getMetadata().getVersion().getFriendlyString().equals("26.3") ? "isPermanentlyInvulnerable" : "isInvulnerable";
                require((boolean)seat.getClass().getMethod(getter).invoke(seat),"Seat is vulnerable");
                player.stopRiding();
                seat.tick();
                require(seat.isRemoved(),"Dismounted seat was not cleaned up automatically");
                // Ownership must distinguish seats whose search boxes overlap vertically.
                var upper=pos.above();
                level.setBlockAndUpdate(upper,chair.defaultBlockState());
                SeatBlock.use(chair.defaultBlockState(),level,upper,player);
                var upperSeat=(ArmorStand)player.getVehicle();
                require(upperSeat!=null,"Upper seat missing");
                SeatBlock.removeSeats(level,pos);
                require(!upperSeat.isRemoved(),"Neighbor parent removed an unrelated seat");
                SeatBlock.removeSeats(level,upper);
                require(upperSeat.isRemoved() && !player.isPassenger(),"Owned seat removal failed");
                // Ownership works even when precise placement moves the mount outside its parent cell.
                var storage=level.getBlockEntity(pos);
                require(storage instanceof PlacementOffsetAccess,"Chair has no placement storage");
                ((PlacementOffsetAccess)storage).mdm$setPlacementOffset(new Vec3(1.5,0.25,-1.5));
                SeatBlock.use(chair.defaultBlockState(),level,pos,player);
                var shiftedSeat=(ArmorStand)player.getVehicle();
                require(shiftedSeat!=null,"Shifted seat missing");
                SeatBlock.removeSeats(level,pos);
                require(shiftedSeat.isRemoved(),"Shifted parent seat survived removal");
                ((PlacementOffsetAccess)storage).mdm$setPlacementOffset(null);
                // An ordinary armor stand must never be treated as a library seat.
                var ordinary=new ArmorStand(level,0.5,200,0.5);
                level.addFreshEntity(ordinary);
                ordinary.tick();
                require(!ordinary.isRemoved(),"Ordinary armor stand was cleaned up");
                ordinary.discard();
                verified=true;
                server.halt(false);
            } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(1); }
        });
    }
}
