package com.cookiecraftmods.eqlib.block;

import java.util.List;
import java.util.HashMap;
import java.util.Map;


import net.minecraft.util.math.BlockPos;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.RegistryObject;

public final class SeatBlock {
	private static final String SEAT_TAG = "eqlib:seat";
    private static final String OWNER_TAG = "eqlib:seat_owner:";
    private static final Map<World, Map<String, java.lang.ref.WeakReference<ArmorStandEntity>>> SEATS = new java.util.WeakHashMap<>();

    private static String ownerTag(BlockPos pos) { return OWNER_TAG + pos.asLong(); }

    private static boolean legacyPositionMatches(ArmorStandEntity seat, World level, BlockPos pos) {
        var offset = com.cookiecraftmods.eqlib.placement.PrecisePlacement.offset(level, pos);
        return Math.abs(seat.getX() - (pos.getX() + 0.5 + offset.x)) < 0.01
                && Math.abs(seat.getY() - (getSeatY(level.getBlockState(pos), pos) + offset.y)) < 0.01
                && Math.abs(seat.getZ() - (pos.getZ() + 0.5 + offset.z)) < 0.01;
    }

    private static void track(World level, ArmorStandEntity seat) {
        for (String tag : seat.getTags()) {
            if (tag.startsWith(OWNER_TAG)) {
                SEATS.computeIfAbsent(level, ignored -> new HashMap<>())
                        .put(tag, new java.lang.ref.WeakReference<>(seat));
                return;
            }
        }
    }

    /** Called by the library's armor-stand tick hook, including for restored seats. */
    public static void tickSeat(World level, ArmorStandEntity seat) {
        if (level.isClientSide || !isSeat(seat)) return;
        if (!seat.isVehicle()) {
            seat.remove();
            Map<String, java.lang.ref.WeakReference<ArmorStandEntity>> seats = SEATS.get(level);
            if (seats != null) seats.values().removeIf(ref -> ref.get() == null || ref.get() == seat);
        } else track(level, seat);
    }
	private static final double DEFAULT_SEAT_HEIGHT = 7.0D / 16.0D;
	private static final double RIDER_HEIGHT_CORRECTION = 2.0D;
	private static final Map<ResourceLocation, Double> SEAT_HEIGHTS = new HashMap<>();

	private SeatBlock() {
	}

	public static void setSeatHeight(RegistryObject<Block> block, double seatHeight) {
		if (!Double.isFinite(seatHeight)) throw new IllegalArgumentException("Seat height must be finite");
		SEAT_HEIGHTS.put(block.getId(), seatHeight);
	}

	public static void setSeatHeightPixels(RegistryObject<Block> block, double seatHeightPixels) {
		setSeatHeight(block, seatHeightPixels / 16.0D);
	}

	public static ActionResultType use(BlockState state, World level, BlockPos pos, PlayerEntity player) {
		if (!com.cookiecraftmods.eqlib.api.FurnitureApi.sittable(state) || player.isShiftKeyDown() || player.isPassenger()) {
			return ActionResultType.PASS;
		}
		if (!level.isClientSide) {
			var offset = com.cookiecraftmods.eqlib.placement.PrecisePlacement.offset(level, pos);
			List<ArmorStandEntity> seats = level.getEntitiesOfClass(ArmorStandEntity.class, new AxisAlignedBB(pos).move(offset).inflate(0, RIDER_HEIGHT_CORRECTION + 1, 0), candidate -> isSeat(candidate) && (candidate.getTags().contains(ownerTag(pos)) || (candidate.getTags().stream().noneMatch(tag -> tag.startsWith(OWNER_TAG)) && legacyPositionMatches(candidate, level, pos))));
            Map<String, java.lang.ref.WeakReference<ArmorStandEntity>> tracked = SEATS.get(level);
            java.lang.ref.WeakReference<ArmorStandEntity> reference = tracked == null ? null : tracked.get(ownerTag(pos));
            ArmorStandEntity existing = reference == null ? null : reference.get();
            if (existing != null && existing.isAlive() && !seats.contains(existing)) seats.add(existing);

			ArmorStandEntity seat = seats.stream().filter(candidate -> candidate.getPassengers().isEmpty()).findFirst().orElse(null);
			if (seat == null && seats.isEmpty()) {
				seat = new ArmorStandEntity(level, pos.getX() + 0.5D + offset.x, getSeatY(state, pos) + offset.y, pos.getZ() + 0.5D + offset.z);
				seat.setNoGravity(true);
				seat.setInvulnerable(true);
				seat.setInvisible(true);
				seat.addTag(SEAT_TAG);
                seat.addTag(ownerTag(pos));
                track(level, seat);
				level.addFreshEntity(seat);
			}
			if (seat != null) {
                seat.addTag(ownerTag(pos));
                track(level, seat);
				seat.setPos(pos.getX() + 0.5D + offset.x, getSeatY(state, pos) + offset.y, pos.getZ() + 0.5D + offset.z);
				if (state.hasProperty(HorizontalBlock.FACING)) {
					seat.yRot = state.getValue(HorizontalBlock.FACING).toYRot();
				}
				player.startRiding(seat, true);
			}
		}
		return ActionResultType.SUCCESS;
	}

	private static double getSeatY(BlockState state, BlockPos pos) {
		return pos.getY() + SEAT_HEIGHTS.getOrDefault(Registry.BLOCK.getKey(state.getBlock()), DEFAULT_SEAT_HEIGHT) - RIDER_HEIGHT_CORRECTION;
	}

	private static boolean isSeat(ArmorStandEntity entity) {
		return entity.getTags().contains(SEAT_TAG) || entity.getTags().contains("mdm:seat");
	}

    /** Compatibility hook; cleanup now also runs automatically on each seat tick. */
    public static void removeEmptySeats(net.minecraft.world.server.ServerWorld level) {
        Map<String, java.lang.ref.WeakReference<ArmorStandEntity>> seats = SEATS.get(level);
        if (seats == null) return;
        for (java.lang.ref.WeakReference<ArmorStandEntity> ref : new java.util.ArrayList<>(seats.values())) {
            ArmorStandEntity seat = ref.get();
            if (seat != null) tickSeat(level, seat);
        }
        seats.values().removeIf(ref -> ref.get() == null);
    }

    public static void removeSeats(World level, BlockPos pos) {
        if (level.isClientSide) return;
        Map<String, java.lang.ref.WeakReference<ArmorStandEntity>> seats = SEATS.get(level);
        java.lang.ref.WeakReference<ArmorStandEntity> ref = seats == null ? null : seats.remove(ownerTag(pos));
        ArmorStandEntity owned = ref == null ? null : ref.get();
        if (owned != null) {
            owned.ejectPassengers();
            owned.remove();
        }
        // Include the mount's lowered origin; retain support for seats from older saves.
        var offset = com.cookiecraftmods.eqlib.placement.PrecisePlacement.offset(level, pos);
        for (ArmorStandEntity seat : level.getEntitiesOfClass(ArmorStandEntity.class,
                new AxisAlignedBB(pos).move(offset).inflate(0, RIDER_HEIGHT_CORRECTION + 1, 0),
                candidate -> isSeat(candidate) && (candidate.getTags().contains(ownerTag(pos))
                    || (candidate.getTags().stream().noneMatch(tag -> tag.startsWith(OWNER_TAG)) && legacyPositionMatches(candidate, level, pos))))) {
            seat.ejectPassengers();
            seat.remove();
        }
    }
}
