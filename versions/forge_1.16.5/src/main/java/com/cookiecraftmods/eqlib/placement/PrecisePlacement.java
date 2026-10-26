package com.cookiecraftmods.eqlib.placement;

import com.cookiecraftmods.eqlib.block.FurnitureFootprint;
import com.cookiecraftmods.eqlib.block.FurniturePartBlock;
import com.cookiecraftmods.eqlib.block.MulticellFurniture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.shapes.VoxelShape;
import java.util.function.Supplier;

public final class PrecisePlacement {
    private PrecisePlacement() {}
    private static final ThreadLocal<Integer> RAW = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Plan> PENDING = new ThreadLocal<>();
    public static java.util.function.Function<BlockPos, Vector3d> clientOffset = pos -> Vector3d.ZERO;
    public static java.util.function.BiConsumer<TileEntity, Vector3d> clientDataChanged = (entity, offset) -> {};
    public static java.util.function.Consumer<TileEntity> clientDataRemoved = entity -> {};

    public static final class Plan {
private final BlockState state;
private final BlockPos pos;
private final Vector3d offset;
private final VoxelShape shape;
private final boolean valid;
public Plan(BlockState state, BlockPos pos, Vector3d offset, VoxelShape shape, boolean valid) {this.state = state;this.pos = pos;this.offset = offset;this.shape = shape;this.valid = valid;}
public BlockState state() { return state; }
public BlockPos pos() { return pos; }
public Vector3d offset() { return offset; }
public VoxelShape shape() { return shape; }
public boolean valid() { return valid; }
}
    public static boolean supported(Block block) {
        return com.cookiecraftmods.eqlib.api.FurnitureApi.supports(block)
                && !(block instanceof FurniturePartBlock);
    }

    public static boolean isPlanning() { return RAW.get() > 0; }

    public static <T> T raw(Supplier<T> action) {
        RAW.set(RAW.get() + 1);
        try { return action.get(); }
        finally { RAW.set(RAW.get() - 1); }
    }

    public static boolean active(IBlockReader level, BlockPos pos) {
        if (level == null || isPlanning()) return false;
        Plan plan = PENDING.get();
        if (plan != null && plan.pos.equals(pos)) return true;
        return level.getBlockEntity(pos) instanceof PlacementOffsetAccess data && data.mdm$getPlacementOffset() != null;
    }

    public static Vector3d offset(IBlockReader level, BlockPos pos) {
        if (level == null || isPlanning()) return Vector3d.ZERO;
        Plan plan = PENDING.get();
        if (plan != null && plan.pos.equals(pos)) return plan.offset;
        if (level.getBlockEntity(pos) instanceof PlacementOffsetAccess data && data.mdm$getPlacementOffset() != null) {
            return data.mdm$getPlacementOffset();
        }
        return Vector3d.ZERO;
    }

    public static VoxelShape rawShape(BlockState state, IBlockReader level, BlockPos pos, boolean collision) {
        return raw(() -> {
            if (state.getBlock() instanceof MulticellFurniture furniture) {
                return collision ? furniture.getFurnitureCollisionShape(state, level, pos, ISelectionContext.empty())
                        : furniture.getFurnitureShape(state, level, pos, ISelectionContext.empty());
            }
            return collision ? state.getCollisionShape(level, pos) : state.getShape(level, pos);
        });
    }

    public static VoxelShape fullShape(BlockState state, IBlockReader level, BlockPos pos, boolean collision) {
        Vector3d offset = offset(level, pos);
        return rawShape(state, level, pos, collision).move(offset.x, offset.y, offset.z);
    }
    public static boolean requested(BlockItemUseContext context) {
        return !isPlanning() && context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                && context.getItemInHand().getItem() instanceof BlockItem item && supported(item.getBlock());
    }
    public static Plan plan(BlockItemUseContext context) {
        Block block = ((BlockItem) context.getItemInHand().getItem()).getBlock();
        BlockPos pos = context.getClickedPos().immutable();
        BlockState candidate = raw(() -> block.getStateForPlacement(context));
        if (candidate == null) candidate = block.defaultBlockState();
        Direction face = context.getClickedFace();
        if (candidate.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && face.getAxis() != Direction.Axis.Y) {
            candidate = candidate.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        }
        VoxelShape shape = rawShape(candidate, context.getLevel(), pos, false);
        if (shape.isEmpty()) return new Plan(candidate, pos, Vector3d.ZERO, shape, false);
        Vector3d offset = align(shape.bounds(), face, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        VoxelShape shifted = shape.move(offset.x, offset.y, offset.z);
        VoxelShape collision = rawShape(candidate, context.getLevel(), pos, true)
                .move(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
        boolean valid = fits(context.getLevel(), pos, shifted) && candidate.canSurvive(context.getLevel(), pos)
                && context.getLevel().isUnobstructed(null, collision);
        return new Plan(candidate, pos, offset, shifted, valid);
    }

    public static Vector3d align(AxisAlignedBB bounds, Direction face, Vector3d hit) {
        double x = Math.rint(hit.x * 16) / 16 - (bounds.minX + bounds.maxX) / 2;
        double y = Math.rint(hit.y * 16) / 16 - (bounds.minY + bounds.maxY) / 2;
        double z = Math.rint(hit.z * 16) / 16 - (bounds.minZ + bounds.maxZ) / 2;
        return switch (face) {
            case EAST -> new Vector3d(hit.x - bounds.minX, y, z);
            case WEST -> new Vector3d(hit.x - bounds.maxX, y, z);
            case UP -> new Vector3d(x, hit.y - bounds.minY, z);
            case DOWN -> new Vector3d(x, hit.y - bounds.maxY, z);
            case SOUTH -> new Vector3d(x, y, hit.z - bounds.minZ);
            case NORTH -> new Vector3d(x, y, hit.z - bounds.maxZ);
        };
    }

    public static boolean fits(World level, BlockPos pos, VoxelShape shape) {
        if (shape.isEmpty()) return false;
        for (BlockPos offset : FurnitureFootprint.offsets(shape)) {
            if (!FurniturePartBlock.X.getPossibleValues().contains(offset.getX() + 4)
                    || !FurniturePartBlock.Y.getPossibleValues().contains(offset.getY() + 4)
                    || !FurniturePartBlock.Z.getPossibleValues().contains(offset.getZ() + 4)) return false;
            BlockPos cell = pos.offset(offset);
            if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).getMaterial().isReplaceable()) return false;
        }
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos).getMaterial().isReplaceable();
    }

    public static void begin(Plan plan) { PENDING.set(plan); }
    public static void end() { PENDING.remove(); }
    public static void commit(World level, BlockPos pos) {
        Plan plan = PENDING.get();
        if (plan == null || !plan.pos.equals(pos)) return;
        TileEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof PlacementOffsetAccess data)) {
            throw new IllegalStateException("Decoration is missing placement storage: " + plan.state);
        }
        data.mdm$setPlacementOffset(plan.offset);
        entity.setChanged();
        FurnitureFootprint.placeParts(level, pos, plan.state);
        level.sendBlockUpdated(pos, plan.state, plan.state, 3);
    }

    public static void refresh(TileEntity entity) {
        World level = entity.getLevel();
        if (level != null) {
            BlockState state = entity.getBlockState();
            level.sendBlockUpdated(entity.getBlockPos(), state, state, 3);
        }
    }
}