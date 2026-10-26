package com.cookiecraftmods.eqlib.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;

public interface MulticellFurniture {
    VoxelShape getFurnitureShape(BlockState state, IBlockReader level, BlockPos pos, ISelectionContext context);

    default VoxelShape getFurnitureCollisionShape(BlockState state, IBlockReader level, BlockPos pos, ISelectionContext context) {
        return getFurnitureShape(state, level, pos, context);
    }
}
