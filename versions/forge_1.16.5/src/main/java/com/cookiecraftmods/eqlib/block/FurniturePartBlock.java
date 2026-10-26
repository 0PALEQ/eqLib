package com.cookiecraftmods.eqlib.block;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.block.BlockRenderType;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.block.IWaterLoggable;


import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import java.util.Random;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.block.*;
import net.minecraft.util.Rotation;
import net.minecraft.util.Mirror;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateContainer;


import net.minecraft.util.math.BlockRayTraceResult;


public class FurniturePartBlock extends Block implements IWaterLoggable {
    public static final IntegerProperty X = IntegerProperty.create("offset_x", 0, 8);
    public static final IntegerProperty Y = IntegerProperty.create("offset_y", 0, 8);
    public static final IntegerProperty Z = IntegerProperty.create("offset_z", 0, 8);
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public FurniturePartBlock(AbstractBlock.Properties properties) {
        super(properties.strength(1f, 10f).noOcclusion().dynamicShape()
                .isRedstoneConductor((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any().setValue(X, 1).setValue(Y, 1).setValue(Z, 1).setValue(WATERLOGGED, false).setValue(EXTENDED, false));
    }

    public static BlockState forOffset(BlockPos offset, BlockState replaced, Block owner) {
        return com.cookiecraftmods.eqlib.api.FurnitureApi.helper(owner).defaultBlockState()
                .setValue(EXTENDED, true).setValue(X, offset.getX() + 4).setValue(Y, offset.getY() + 4).setValue(Z, offset.getZ() + 4)
                .setValue(WATERLOGGED, replaced.getFluidState().getType() == Fluids.WATER);
    }

    public static BlockPos parentPos(BlockState state, BlockPos pos) {
        int center = state.getValue(EXTENDED) ? 4 : 1;
        return pos.offset(center - state.getValue(X), center - state.getValue(Y), center - state.getValue(Z));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.IBlockReader level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, net.minecraft.world.IBlockReader level, BlockPos pos) {
        return 0;
    }

    @Override
    public net.minecraft.block.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.block.material.PushReaction.BLOCK;
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z, WATERLOGGED, EXTENDED);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader level, BlockPos pos, ISelectionContext context) {
        return FurnitureFootprint.partShape(level, pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader level, BlockPos pos, ISelectionContext context) {
        return FurnitureFootprint.partCollisionShape(level, pos, state);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, IBlockReader level, BlockPos pos, ISelectionContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderShape(BlockState state) { return BlockRenderType.INVISIBLE; }



    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        int center = state.getValue(EXTENDED) ? 4 : 1;
        BlockPos offset = new BlockPos(state.getValue(X) - center, state.getValue(Y) - center, state.getValue(Z) - center).rotate(rotation);
        return state.setValue(X, offset.getX() + center).setValue(Z, offset.getZ() + center);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        int center = state.getValue(EXTENDED) ? 4 : 1;
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(Z, 2 * center - state.getValue(Z));
            case FRONT_BACK -> state.setValue(X, 2 * center - state.getValue(X));
            default -> state;
        };
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockItemUseContext context) {
        if (!(context.getItemInHand().getItem() instanceof BlockItem item)) return false;
        BlockPos parent = parentPos(state, context.getClickedPos());
        BlockState candidate = item.getBlock().defaultBlockState();
        if (candidate.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            candidate = candidate.setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
        }
        return FurnitureFootprint.compatible(candidate, context.getClickedPos(), context.getLevel().getBlockState(parent), parent);
    }

    @Override
    public void playerWillDestroy(World level, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!level.isClientSide && player.isCreative() && FurnitureFootprint.validPart(level, pos, state)) {
            FurnitureFootprint.destroyParents(level, pos, state, false);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void tick(BlockState state, ServerWorld level, BlockPos pos, Random random) {
        BlockPos parent = parentPos(state, pos);
        if (level.hasChunkAt(parent) && !FurnitureFootprint.validPart(level, pos, state)) {
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        }
    }


    @Override
    public ItemStack getCloneItemStack(IBlockReader level, BlockPos pos, BlockState state) {
        if (!FurnitureFootprint.validPart(level, pos, state)) return ItemStack.EMPTY;
        BlockPos parent = parentPos(state, pos);
        BlockState owner = level.getBlockState(parent);
        return owner.getBlock().getCloneItemStack(level, parent, owner);
    }

    private static ActionResultType useParent(World level, BlockPos parent, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        BlockState owner = level.getBlockState(parent);
        ActionResultType lightResult = com.cookiecraftmods.eqlib.api.FurnitureApi.interact(owner, level, parent, player);
        if (lightResult.consumesAction()) return lightResult;
        ActionResultType result = SeatBlock.use(owner, level, parent, player);
        return result.consumesAction() ? result : owner.use(level, player, hand, parentHit(hit, parent));
    }

    private static BlockRayTraceResult parentHit(BlockRayTraceResult hit, BlockPos parent) {
        return new BlockRayTraceResult(hit.getLocation(), hit.getDirection(), parent, hit.isInside());
    }

    @Override
    public ActionResultType use(BlockState state, World level, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        BlockPos parent = parentPos(state, pos);
        return FurnitureFootprint.validPart(level, pos, state)
                ? useParent(level, parent, player, hand, hit) : ActionResultType.PASS;
    }

    @Override
    public void onRemove(BlockState state, World level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) FurnitureFootprint.partRemoved(level, pos, state, replacement);
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.util.Direction direction, BlockState neighborState,
            net.minecraft.world.IWorld level, BlockPos pos, BlockPos neighborPos) {
        level.getBlockTicks().scheduleTick(pos, this, 1);
        if (state.getValue(WATERLOGGED)) level.getLiquidTicks().scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }

}
