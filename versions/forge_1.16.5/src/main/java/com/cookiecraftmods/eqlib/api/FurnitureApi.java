package com.cookiecraftmods.eqlib.api;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import java.util.*;
import java.util.function.*;

/** Register once, before constructing your blocks. Keep helper IDs owned by the consumer. */
public final class FurnitureApi {
    private FurnitureApi() {}
    private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();
    public interface Compatibility { boolean test(BlockState a, BlockPos ap, BlockState b, BlockPos bp); }
    private static final class Adapter {
        final Supplier<Block> helper;
        final Predicate<BlockState> sittable;
        final Function<BlockState, Vector3d> offset;
        final Compatibility compatibility;
        Adapter(Supplier<Block> h, Predicate<BlockState> s, Function<BlockState, Vector3d> o, Compatibility c) {
            helper=h; sittable=s; offset=o; compatibility=c;
        }
    }
    public static synchronized void register(String packagePrefix, Supplier<Block> helper,
            Predicate<BlockState> sittable, Function<BlockState, Vector3d> modelOffset, Compatibility compatibility) {
        Objects.requireNonNull(packagePrefix);
        if (!packagePrefix.endsWith(".") || packagePrefix.length() < 2) throw new IllegalArgumentException("Use a full block package ending in a dot");
        for (String existing : ADAPTERS.keySet()) {
            if (existing.startsWith(packagePrefix) || packagePrefix.startsWith(existing)) throw new IllegalArgumentException("Overlapping furniture packages: " + packagePrefix);
        }
        ADAPTERS.put(packagePrefix, new Adapter(Objects.requireNonNull(helper), Objects.requireNonNull(sittable), Objects.requireNonNull(modelOffset), Objects.requireNonNull(compatibility)));
    }
    private static Adapter adapter(Class<?> type) {
        for (Map.Entry<String, Adapter> entry : ADAPTERS.entrySet()) if (type.getName().startsWith(entry.getKey())) return entry.getValue();
        return null;
    }
    public static boolean supportsClass(Class<?> type) { return adapter(type) != null; }
    public static boolean supports(Block block) { return supportsClass(block.getClass()); }
    public static Block helper(Block owner) {
        Adapter a = adapter(owner.getClass());
        if (a == null) throw new IllegalStateException("Unregistered furniture: " + owner);
        return Objects.requireNonNull(a.helper.get(), "Helper has not been registered");
    }
    public static boolean sittable(BlockState state) { Adapter a=adapter(state.getBlock().getClass()); return a != null && a.sittable.test(state); }
    public static Vector3d modelOffset(BlockState state) { Adapter a=adapter(state.getBlock().getClass()); return a == null ? Vector3d.ZERO : a.offset.apply(state); }
    public static boolean compatible(BlockState a, BlockPos ap, BlockState b, BlockPos bp) {
        Adapter adapter=adapter(a.getBlock().getClass());
        return adapter != null && adapter == adapter(b.getBlock().getClass()) && adapter.compatibility.test(a, ap, b, bp);
    }

    public interface Interaction { net.minecraft.util.ActionResultType use(BlockState state, net.minecraft.world.World level, BlockPos pos, net.minecraft.entity.player.PlayerEntity player); }
    private static final Map<String, Interaction> INTERACTIONS = new LinkedHashMap<>();
    public static void registerInteraction(String prefix, Interaction action) { INTERACTIONS.put(prefix, Objects.requireNonNull(action)); }
    public static net.minecraft.util.ActionResultType interact(BlockState state, net.minecraft.world.World level, BlockPos pos, net.minecraft.entity.player.PlayerEntity player) {
        for (Map.Entry<String, Interaction> e : INTERACTIONS.entrySet()) if (state.getBlock().getClass().getName().startsWith(e.getKey())) return e.getValue().use(state, level, pos, player);
        return net.minecraft.util.ActionResultType.PASS;
    }
}
