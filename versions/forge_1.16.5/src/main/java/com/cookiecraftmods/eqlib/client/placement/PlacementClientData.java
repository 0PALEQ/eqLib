package com.cookiecraftmods.eqlib.client.placement;
import com.cookiecraftmods.eqlib.placement.PrecisePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.util.math.vector.Vector3d;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable offsets are safe to query from chunk meshing threads, without reading live block entity maps. */
public final class PlacementClientData {
    private static final ConcurrentHashMap<BlockPos, Vector3d> OFFSETS = new ConcurrentHashMap<>();
    private static volatile IBlockReader world;
    private PlacementClientData() {}
    public static void tick() {
        if (world != Minecraft.getInstance().level) {
            OFFSETS.clear();
            world = Minecraft.getInstance().level;
        }
    }
    public static void install() {
        PrecisePlacement.clientOffset = pos -> world == Minecraft.getInstance().level ? OFFSETS.getOrDefault(pos, Vector3d.ZERO) : Vector3d.ZERO;
        PrecisePlacement.clientDataChanged = (entity, offset) -> {
            if (entity.getLevel() == null || entity.getLevel() != Minecraft.getInstance().level) return;
            tick();
            if (offset == null) OFFSETS.remove(entity.getBlockPos());
            else OFFSETS.put(entity.getBlockPos(), offset);
        };
        PrecisePlacement.clientDataRemoved = entity -> {
            if (entity.getLevel() == world) OFFSETS.remove(entity.getBlockPos());
        };
    }
}
