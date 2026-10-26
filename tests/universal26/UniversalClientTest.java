package com.cookiecraftmods.eqlib.tests;

import com.cookiecraftmods.eqlib.client.placement.PlacementPreview;
import com.cookiecraftmods.eqlib.block.FurnitureFootprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;

/** Exercises the shared preview mixin in the real renderer on both supported games. */
public final class UniversalClientTest implements ClientModInitializer {
    private int ticks,phase,startup;
    private boolean capturing;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (++startup>2400) throw new AssertionError("Universal client timed out");
                if (mc.player==null || mc.level==null || mc.getSingleplayerServer()==null || capturing) return;
                if (ticks++==0) {
                    mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.getSingleplayerServer().execute(() -> {
                        var level=mc.getSingleplayerServer().overworld();
                        for (var pos:BlockPos.betweenClosed(new BlockPos(-8,190,-8),new BlockPos(8,212,8))) {
                            level.setBlockAndUpdate(pos,pos.getY()==199 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        level.setBlockAndUpdate(new BlockPos(0,201,3),Blocks.DIRT.defaultBlockState());
                        var trash=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
                        for (var entity:level.getAllEntities()) {
                            if (entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) trash.add(entity);
                        }
                        trash.forEach(net.minecraft.world.entity.Entity::discard);
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.stopRiding();
                        player.getInventory().clearContent();
                        mc.getSingleplayerServer().getCommands().performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(),"time set day");
                        player.connection.teleport(0.5,200,0.5,0,0);
                        var guitar=BuiltInRegistries.BLOCK.stream().filter(b -> b.getClass().getSimpleName().equals("ElectricGuitarBlackBlock")).findFirst().orElseThrow();
                        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(guitar.asItem()));
                        player.inventoryMenu.broadcastChanges();
                    });
                }
                mc.options.keyShift.setDown(true);
                mc.player.setYRot(0);
                mc.player.setXRot(0);
                if (ticks<100 || (phase==1 && ticks<130)) return;
                var plan=PlacementPreview.current();
                if (plan==null) throw new AssertionError("No placement preview");
                if (phase==0 && !plan.valid()) throw new AssertionError("Initial preview is invalid");
                if (phase==1 && plan.valid()) {
                    if (ticks>200) throw new AssertionError("Blocked preview is still valid");
                    return;
                }
                capturing=true;
                var imagePath=mc.gameDirectory.toPath().resolve(phase==0 ? "universal-preview-valid.png" : "universal-preview-blocked.png");
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image -> {
                    try (image) {
                        image.writeToFile(imagePath);
                        if (phase++==0) {
                            var obstruction=plan.pos().offset(FurnitureFootprint.offsets(plan.shape()).getFirst());
                            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setBlockAndUpdate(obstruction,Blocks.STONE.defaultBlockState()));
                            ticks=100;
                            capturing=false;
                        } else {
                            Files.writeString(mc.gameDirectory.toPath().resolve("universal-client-result.txt"),"UNIVERSAL_CLIENT_PASSED valid+blocked previews rendered");
                            System.out.println("UNIVERSAL_CLIENT_PASSED");
                            mc.stop();
                        }
                    } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(1); }
                });
            } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(1); }
        });
    }
}
