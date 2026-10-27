# opaleq's Library API guide

This guide describes **eqlib** for mod authors. Start with [the supported target matrix](../README.md#supported-targets) and select the exact Minecraft version and loader you build against. One universal runtime JAR covers that explicit matrix. Compile against the named development API for your exact target using the setup below. All integration code belongs in your own mod; editing eqlib source is not required.

Java examples use **Minecraft 1.21.1 Mojang mappings** unless a different target is named. They illustrate integration points; names such as `ExampleBlocks`, `ExampleBlockEntities`, and `ExampleTags` refer to registries you implement in your own mod. Loader registration code and Minecraft override signatures must match your target.

## Contents

- [Choose an integration path](#choose-an-integration-path)
- [Install the dependency](#install-the-dependency)
- [Initialization order and ownership](#initialization-order-and-ownership)
- [Register your furniture adapter](#register-your-furniture-adapter)
- [Register a helper block](#register-a-helper-block)
- [Integrate multicell furniture](#integrate-multicell-furniture)
- [Enable precise placement](#enable-precise-placement)
- [Add seating](#add-seating)
- [Older helper interactions](#older-helper-interactions)
- [API reference](#api-reference)
- [Neutral clock utilities](#neutral-clock-utilities)
- [Version differences](#version-differences)
- [Compatibility and troubleshooting](#compatibility-and-troubleshooting)
- [Validation checklist](#validation-checklist)

## Choose an integration path

| What you want | What to implement |
| --- | --- |
| Clock angles or hand meshes | Install the dependency and use the [clock utilities](#neutral-clock-utilities). No furniture adapter or helper registration is needed. |
| Furniture extending outside its parent cell | Register an adapter and helper, implement `MulticellFurniture`, and add the [placement and removal hooks](#integrate-multicell-furniture). |
| Shift-based precise placement | Register an adapter and helper, provide a parent block entity, and follow [precise placement](#enable-precise-placement). Implement `MulticellFurniture` if your ordinary shape methods clip geometry to the parent cell. |
| Sittable furniture | Register an adapter, forward interaction to `SeatBlock.use`, and remove seats when the parent is removed. See [seating](#add-seating). |

Registering a furniture package also enables shift-based precise placement for its blocks (except helper blocks). There is no separate seating-only or multicell-only opt-in flag. Every block in that package that players can place with a `BlockItem` therefore needs placement storage, even if you primarily want seating or ordinary multicell placement.

For a first integration, use one block, a helper, a placement-data block entity, zero model offset, and a compatibility callback returning `false`. Verify normal placement, shift placement, breaking a helper, and save/reload before adding shared cells or custom rendering.

## Install the dependency

Obtain the universal runtime and the matching development API artifact from the library maintainer. The runtime alone is not the compile-time API. No public Maven endpoint is configured in this repository; use a maintainer-provided Maven repository or a copy of the published `maven/` directory. You do not need a library source checkout to use those artifacts.

Set `eqlib_version` in your mod's `gradle.properties` to the supplied release version. Add the following to your mod's `build.gradle`, replacing the example path with the actual repository location. If you want to build the dependency from source yourself, follow the optional [maintainer build instructions](UNIVERSAL.md#build-and-publish).

```groovy
repositories {
    maven { url = uri('path/to/eqlib-maven-repository') }
}
dependencies {
    // Example: Mojang-mapped Fabric 1.21.1. Select your own loader/game classifier.
    compileOnly "com.cookiecraftmods:eqlib:${eqlib_version}:dev-fabric_1.21.1"
    runtimeOnly "com.cookiecraftmods:eqlib:${eqlib_version}"
}
```

The main artifact is the same universal runtime on every supported target. Classifiers follow `dev-<loader>_<minecraft>`, for example `dev-forge_1.16.5` or `dev-neoforge_26.2`. Keep the API classifier compile-only. It uses the library adapter's named mappings; the supported development projects use Mojang mappings. An independently configured Yarn-mapped consumer must translate the compile API to its own mappings. Do not remap or reobfuscate the outer universal artifact. The shared Fabric 26.2/26.3 implementation is published as separate `dev-fabric_26.2` and `dev-fabric_26.3` classifiers; do not use `dev-fabric_26.2-26.3`.

For **Fabric**, put the main artifact on the ordinary runtime class path; development mode is detected automatically. For **Forge/NeoForge**, copy the resolved main artifact into your development run's `mods/` directory before launch so early discovery can find its loader bridge. Use a Gradle `Copy` task and make the game run depend on it so the copy happens before the JVM starts. Set the JVM system property `eqlib.development=true` through those loader run configurations, for development runs only. Never copy the API classifier into `mods/`. Standalone Java verification programs can add the named API classifier to their own runtime class path; game runs must load only the universal runtime.

Keep your own Minecraft and Fabric API dependencies matched to your game. Neither the universal publication nor its compile API pulls a game-specific Fabric API into your mod. Install one runtime per instance and remove superseded eqlib runtime files when updating.

In the metadata snippets below, replace `<compatible-library-range>` with the supported release range for your integration and `examplemod` with your mod ID.

### Fabric metadata

Add to the existing `depends` object in `fabric.mod.json`:

```json
"eqlib": "<compatible-library-range>"
```

### Forge metadata

Add to `META-INF/mods.toml`:

```toml
[[dependencies.examplemod]]
modId="eqlib"
mandatory=true
versionRange="<compatible-library-range>"
ordering="AFTER"
side="BOTH"
```

### NeoForge metadata

Add to `META-INF/neoforge.mods.toml`:

```toml
[[dependencies.examplemod]]
modId="eqlib"
type="required"
versionRange="<compatible-library-range>"
ordering="AFTER"
side="BOTH"
```

Install one universal eqlib runtime alongside your mod on **both clients and servers**. Do not install development API classifiers, shade library classes into your mod, or register its mixin configuration again.

## Initialization order and ownership

Your mod owns its registered blocks, helper block, items, block entity types, tags, models, recipes, and any content-specific behavior. The library provides reusable implementation code and installs the core placement mixins itself.

Use this initialization order:

1. Register your adapter **before your block constructors execute**. Dynamic shape behavior is established during construction.
2. Register your helper and furniture blocks, then the appropriate block entity types and items using your loader's registration lifecycle.
3. Configure seat heights with the overload appropriate to your target; preserve deferred registration where required.
4. Wire interactions and parent-removal cleanup, and provide your content resources.

Call registration once during initialization. There is no unregister/reset API; registrations are process-wide, not per world. Do not register adapters in a world tick or on each world load.

## Register your furniture adapter

Entry point: `com.cookiecraftmods.eqlib.api.FurnitureApi`.

```java
import com.cookiecraftmods.eqlib.api.FurnitureApi;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

// Call before constructing blocks in this package.
FurnitureApi.register(
        "org.example.examplemod.block.",
        ExampleBlocks::helperBlock,
        state -> state.is(ExampleTags.SITTABLE),
        state -> Vec3.ZERO,
        (a, aPos, b, bPos) -> false
);
```

Define `helperBlock()` in your registry holder as a static method returning the registered helper `Block`. A method reference avoids evaluating a deferred registry entry during initialization:

```java
public static Block helperBlock() {
    return FURNITURE_PART.get();
}
```

`FURNITURE_PART` here is your loader-specific registry holder. If your registry stores blocks directly, return that block instead.

| Argument | Contract |
| --- | --- |
| `packagePrefix` | Full Java package prefix ending in a dot. Matching uses the concrete block class name, including subpackages; it does not use the registry namespace. |
| `helper` | Lazy `Supplier<Block>` returning your registered `FurniturePartBlock`. It must be available when helpers are placed. |
| `sittable` | Predicate on the current `BlockState`. A consumer-owned block tag is a convenient implementation; `state -> false` disables seating. |
| `modelOffset` | State-to-vector function for a separate model/pixel offset. Return a non-null vector; use `Vec3.ZERO` when none is needed. This is distinct from the persistent precise-placement offset. |
| `compatibility` | Returns whether two particular states/positions may share helper cells. Return `false` unless your furniture explicitly supports this. Account for orientation and relative position, and make rules symmetric when either block can own the helper. |

All callbacks must be non-null. Duplicate and overlapping prefixes are rejected with `IllegalArgumentException`. This includes registering a parent package after one of its subpackages. Separate, non-overlapping registrations are allowed.

Registration opts in the entire matching class package. Keep unrelated blocks outside that package. A block whose class comes from another package is not opted in just because its registry ID uses your namespace. `FurnitureApi.compatible` only calls a compatibility callback when both blocks resolve to the **same adapter**, so cross-adapter sharing is disabled.

Helper sharing is intended for compatible vertical neighbors: helper shape merging, shared-parent destruction, and repair scheduling inspect the positions immediately above and below the owner. A callback returning `true` does not implement arbitrary horizontal or distant sharing. Precise-placement occupancy checks require replaceable cells and do not use this compatibility callback to allow sharing.

A nonzero model-offset callback does not add block properties or implement pixel placement for you. Your mod still defines those properties, placement behavior, and corresponding geometry.

## Register a helper block

Class: `com.cookiecraftmods.eqlib.block.FurniturePartBlock`.

Register an instance under an ID owned by your mod, for example `examplemod:furniture_part`:

```java
new FurniturePartBlock(BlockBehaviour.Properties.of())
```

This is the constructor expression for a Minecraft 1.21.1 registry factory. Supply the registry-aware block properties required by newer versions, including their block registry key. Forge 1.16.5 uses `AbstractBlock.Properties` instead.

The helper has no inventory and renders with `RenderShape.INVISIBLE`. It delegates shapes, targeting, interaction, destruction, and orphan cleanup to the parent furniture. It preserves water when occupying or leaving a water cell. You normally do not register an item for it or put it in a creative tab.

For versions expecting blockstate/model resources, a minimal consumer-owned resource pair is:

`assets/examplemod/blockstates/furniture_part.json`:

```json
{"variants":{"":{"model":"examplemod:block/furniture_part"}}}
```

`assets/examplemod/models/block/furniture_part.json`:

```json
{"elements":[]}
```

The implementation stores parent relationships in `offset_x`, `offset_y`, `offset_z`, and `extended`, and also uses `waterlogged`. Extended helper offsets span -4 through +4 on each axis. Keep the helper's registry ID and these properties stable when updating an existing mod. Prefer the footprint API to hand-constructing helper states.

Keep every occupied helper cell within that range, including after a precise offset is applied. Precise planning rejects out-of-range cells; normal `FurnitureFootprint.placement` does not check this limit, and creating an out-of-range helper state will fail. The stored property values are 0 through 8; `forOffset` encodes a cell offset by adding 4.

## Integrate multicell furniture

Implement `com.cookiecraftmods.eqlib.block.MulticellFurniture` on your block. It declares:

```java
VoxelShape getFurnitureShape(BlockState state, BlockGetter level,
        BlockPos pos, CollisionContext context);

VoxelShape getFurnitureCollisionShape(BlockState state, BlockGetter level,
        BlockPos pos, CollisionContext context);
```

`getFurnitureShape` returns the **full unshifted shape**, in coordinates local to the parent, including geometry outside its cell. Override `getFurnitureCollisionShape` only when collision differs; its default returns the furniture shape. Do not apply the persistent precise-placement offset in either method; the library applies it separately.

For ordinary parent-cell shape methods, clip the full shape with `FurnitureFootprint.localShape(shape, BlockPos.ZERO)`. Returning different full and local shapes requires care around planning: keep the full-shape methods free of calls back into `PrecisePlacement.fullShape`, which would recurse.

For example, these methods describe a fixed, two-block-wide solid bench in a `Block` subclass implementing `MulticellFurniture`:

```java
private static final VoxelShape FULL_SHAPE = Block.box(0, 0, 0, 32, 8, 16);

@Override
public VoxelShape getFurnitureShape(BlockState state, BlockGetter level,
        BlockPos pos, CollisionContext context) {
    return FULL_SHAPE;
}

@Override
public VoxelShape getShape(BlockState state, BlockGetter level,
        BlockPos pos, CollisionContext context) {
    return FurnitureFootprint.localShape(
            getFurnitureShape(state, level, pos, context), BlockPos.ZERO);
}

@Override
public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
        BlockPos pos, CollisionContext context) {
    return FurnitureFootprint.localShape(
            getFurnitureCollisionShape(state, level, pos, context), BlockPos.ZERO);
}
```

`Block.box` takes model pixels (16 per block); the resulting `VoxelShape` uses block units. Import `BlockPos` from `net.minecraft.core`, `BlockGetter` from `net.minecraft.world.level`, `BlockState` from `net.minecraft.world.level.block.state`, and `VoxelShape`/`CollisionContext` from `net.minecraft.world.phys.shapes`. The library interfaces are in `com.cookiecraftmods.eqlib.block`. This example has no facing property; directional furniture must return the appropriately rotated full shape for each state. Supply a matching model separately.

Use the footprint methods at these integration points:

| Integration point | Action |
| --- | --- |
| Compute a normal placement state | Pass the candidate state and placement context to `FurnitureFootprint.placement`; honor a `null` result as rejected placement. |
| After successful placement | Call `FurnitureFootprint.placeParts(level, pos, state)`. |
| Before removing/replacing the parent | Call `FurnitureFootprint.removeParts(level, pos, state)` while its old block entity and offset are still accessible. |
| Scheduled parent tick / repair path | Call `placeParts` again to restore appropriate replaceable helper cells. |
| Piston behavior | Use `FurnitureFootprint.IMMOVABLE` with the version's block-properties/piston API so parent/helper relationships cannot be moved independently. |

For example, these hooks validate placement and create helpers on Minecraft 1.21.1 (Mojang mappings). Add them to your `Block` subclass implementing `MulticellFurniture`:

```java
@Override
public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState candidate = super.getStateForPlacement(context);
    return candidate == null ? null : FurnitureFootprint.placement(candidate, context);
}

@Override
public void setPlacedBy(Level level, BlockPos pos, BlockState state,
        LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (!level.isClientSide) {
        FurnitureFootprint.placeParts(level, pos, state);
    }
}
```

Import `FurnitureFootprint` from `com.cookiecraftmods.eqlib.block`, `BlockPlaceContext` from `net.minecraft.world.item.context`, `LivingEntity` from `net.minecraft.world.entity`, and `ItemStack` from `net.minecraft.world.item`. Merge these calls into existing placement hooks so you preserve orientation and other state properties.

The parent-removal hook for that target is:

```java
@Override
public void onRemove(BlockState state, Level level, BlockPos pos,
        BlockState replacement, boolean moving) {
    if (!state.is(replacement.getBlock())) {
        FurnitureFootprint.removeParts(level, pos, state);
    }
    super.onRemove(state, level, pos, replacement, moving);
}
```

Preserve your existing inventory/drop handling when adding these hooks. Do not independently drop the same parent item for every helper. Newer Minecraft versions use different removal callbacks; port the integration point rather than copying this override unchanged.

Normal multicell placement uses your block's placement hook. Shift-based precise placement additionally uses the library's planning and commit path. Implement both if you support both behaviors.

## Enable precise placement

Classes: `com.cookiecraftmods.eqlib.placement.PrecisePlacement` and `PlacementOffsetAccess`.

For blocks in an adapter package, the library's `BlockItem` mixin recognizes shift placement and computes a plan aligned to the clicked surface with model-pixel snapping. Invalid plans reject placement. It commits the offset, creates helper parts, and clears the pending plan after the normal item-placement operation.

A precisely placed block needs a **block entity at its parent position**. Register a consumer-owned block entity type covering every such block. Blocks without inventories can use a simple storage entity:

```java
public final class PlacementDataBlockEntity extends BlockEntity {
    public PlacementDataBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleBlockEntities.PLACEMENT_DATA.get(), pos, state);
    }
}
```

Your block implements `EntityBlock` and creates this entity in `newBlockEntity`. If it already has a container or other block entity, keep that entity; the library mixes the offset interface into `BlockEntity` itself. Do not create a competing second block entity at the same position.

For the simple storage entity above, add this method to your `Block` subclass implementing `EntityBlock` (from `net.minecraft.world.level.block`):

```java
@Override
public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new PlacementDataBlockEntity(pos, state);
}
```

Import `BlockEntity` from `net.minecraft.world.level.block.entity`. Register `ExampleBlockEntities.PLACEMENT_DATA` with `PlacementDataBlockEntity::new` as its factory and include every block using it in the type's valid-block list. The registry holder must be bound before Minecraft creates an instance.

On 1.21.1, the library supplies update tags and packets through the base `BlockEntity` methods when an offset is present; the simple entity above can inherit them. If your entity overrides `getUpdateTag` or `getUpdatePacket`, its implementation must still send the saved offset data: use `saveWithoutMetadata(lookup)` for the update tag and `ClientboundBlockEntityDataPacket.create(this)` for the packet, or merge equivalent behavior into your existing synchronization. Call superclass `saveAdditional` and `loadAdditional` from your persistence overrides so the library's hooks execute. Other targets have different signatures; inspect the matching persistence mixin before porting these methods.

Also add parent-removal cleanup with `FurnitureFootprint.removeParts`, even for a precisely placed block that does not implement `MulticellFurniture`. Its shifted shape may occupy helper cells. Run cleanup before removing the block entity, as shown in the multicell removal example.

The library owns its placement preview and client offset cache. Do not register its mixins twice or replace the public callback fields `clientOffset`, `clientDataChanged`, or `clientDataRemoved`; those fields connect library internals globally across all consumers.

### Read the current offset

```java
Vec3 offset = PrecisePlacement.offset(level, pos);
boolean placedPrecisely = PrecisePlacement.active(level, pos);
```

Use these for your own interaction points, effects, or other position-dependent behavior. Account for the offset when locating seats or effects; the library's own seat implementation already does so.

`PlacementOffsetAccess` exposes `mdm$getPlacementOffset()` and `mdm$setPlacementOffset(Vec3)` (`Vector3d` on Forge 1.16.5). A null stored value means no precise-placement data; a stored zero vector still counts as active precise placement. Prefer `PrecisePlacement.offset` for normal reads because it returns the zero vector when appropriate and respects planning state. Direct writes are an advanced operation: the setter alone does not validate space, rebuild helpers, mark the entity dirty, or perform the full commit process.

Do not manually call `begin`, `commit`, or `end` around normal `BlockItem` placement; the library already manages that lifecycle. A custom item that bypasses `BlockItem` must deliberately implement equivalent planning, validation, persistence, update, and cleanup behavior.

## Add seating

Utility class: `com.cookiecraftmods.eqlib.block.SeatBlock` (it is not a block subclass).

1. Make your adapter's `sittable` predicate recognize the desired block state.
2. Call `SeatBlock.use(state, level, pos, player)` from the block's interaction handler and return its result if it consumes the interaction.
3. Configure seat heights when needed.
4. Empty mounts are removed automatically during their server-side entity tick; no consumer tick handler is required.
5. Call `SeatBlock.removeSeats(level, pos)` before destroying/replacing a seat parent, while its placement offset is still available. `FurnitureFootprint.removeParts` also calls seat removal when its cleanup path applies.

The library owns seat cleanup through its armor-stand tick hook. Seats record their parent position and are tracked independently per level. `removeEmptySeats(serverLevel)` remains available for existing consumers, but only visits tracked seats; remove redundant consumer tick handlers. Historical unowned seats are still recognized by the adapters that previously supported them.

For Fabric 1.21.1, height configuration uses the registered block instance:

```java
SeatBlock.setSeatHeightPixels(ExampleBlocks.CHAIR.get(), 10.0);
```

For a Minecraft 1.21.1 block (Mojang mappings), forward empty-hand interaction like this:

```java
@Override
protected InteractionResult useWithoutItem(BlockState state, Level level,
        BlockPos pos, Player player, BlockHitResult hit) {
    InteractionResult result = SeatBlock.use(state, level, pos, player);
    return result.consumesAction() ? result
            : super.useWithoutItem(state, level, pos, player, hit);
}
```

Import `SeatBlock` from `com.cookiecraftmods.eqlib.block`, `InteractionResult` from `net.minecraft.world`, `Player` from `net.minecraft.world.entity.player`, and `BlockHitResult` from `net.minecraft.world.phys`. Held-item interactions use a separate hook on this target; forward those too if your furniture should allow sitting while holding an item.

Heights must be finite numbers and describe the desired sitting surface relative to the block's base. `setSeatHeight` takes a height in block units; `setSeatHeightPixels` divides the supplied value by 16. The implementation applies its own rider/armor-stand correction; do not pre-subtract that correction. See the version table below before passing a deferred holder or registry ID.

Seating returns `PASS` when the state is not sittable, the player is sneaking, or the player is already riding something. New temporary armor-stand mounts are tagged `eqlib:seat`. Older adapters also recognize legacy seat tags for saved-world compatibility.

A sittable tag is **your mod's** tag: the library does not assume a universal `eqlib:sittable` tag. For example, use `data/examplemod/tags/block/sittable.json` on Minecraft 1.21.1; older versions use the plural `tags/blocks` directory.

```json
{"replace":false,"values":["examplemod:chair"]}
```

`ExampleTags.SITTABLE` in the adapter example must be a block `TagKey` for `examplemod:sittable`. A tag alone does not wire the parent block's interaction handler.

## Older helper interactions

`FurnitureApi.registerInteraction(String prefix, Interaction action)` exists on Forge 1.16.5, Forge 1.19.2, Forge 1.20.1, and Fabric 1.20.1. Register it during initialization if an older helper interaction needs an extra consumer-specific action before seating and parent-block interaction forwarding.

```java
FurnitureApi.registerInteraction("org.example.examplemod.block.",
        ExampleLightSwitch::use);
```

The callback accepts the parent state, world/level, parent position, and player and returns the target's interaction-result type. Return `PASS` when it does not handle the action. Modern targets forward helper interaction through the parent's normal interaction methods and do not expose this registration method.

## API reference

Minecraft parameter type names below follow the 1.21.1 target. For another target, consult its local sources under `versions/<target>/src/main/java` and the shared source directories assigned to it in [`shared/source-groups.json`](../shared/source-groups.json).

### FurnitureApi

| Member | Purpose |
| --- | --- |
| `register(prefix, helper, sittable, modelOffset, compatibility)` | Register one package adapter before constructing consumer blocks. |
| `supportsClass(type)` / `supports(block)` | Query whether a concrete class matches a registered package. `PrecisePlacement.supported` additionally excludes helper blocks. |
| `helper(owner)` | Resolve the registered helper block for an owner. Throws if no adapter exists or its supplier returns null. |
| `sittable(state)` | Evaluate seating eligibility; false for unregistered classes. |
| `modelOffset(state)` | Evaluate the adapter's model-offset function; zero for unregistered classes. |
| `compatible(a, ap, b, bp)` | Evaluate permitted cell sharing within one adapter. |

### FurnitureFootprint

| Member | Purpose |
| --- | --- |
| `fullShape(state, level, pos)` | Full selection shape with the current precise-placement offset. |
| `localShape(full, offset)` | Clip a full shape to a particular cell and translate it into that cell's local coordinates. |
| `offsets(full)` | Enumerate occupied cells relative to the parent, excluding the parent cell itself. |
| `placement(state, context)` | Validate a normal multicell placement; returns the state or null. |
| `placeParts` / `removeParts` | Create replaceable helper cells / remove owned helpers and applicable seats. |
| `validPart` | Check whether a helper still corresponds to occupied parent geometry. |
| `partShape` / `partCollisionShape` | Resolve the local helper selection/collision shape. |
| `partRemoved` / `destroyParents` | Helper destruction plumbing; ordinarily called by `FurniturePartBlock` itself. |
| `IMMOVABLE` | Piston push reaction compatible with the target's enum naming. |

`FurniturePartBlock.forOffset(offset, replaced, owner)` creates a helper state using the adapter for `owner`. It does not place the state or validate a whole placement. `parentPos(state, cell)` resolves a helper's parent. Use these mainly for specialized integrations and tests.

### PrecisePlacement

| Member | Purpose |
| --- | --- |
| `supported(block)` / `requested(context)` | Check eligibility / whether normal item placement requests precise mode. |
| `active(level, pos)` / `offset(level, pos)` | Query stored or pending placement data. |
| `raw(action)` / `isPlanning()` | Temporarily suppress placement offsets while evaluating unshifted shapes. |
| `rawShape` / `fullShape` | Resolve unshifted geometry / geometry with the current precise offset. |
| `plan(context)` | Build a candidate plan for a block item. Only call with an appropriate block-placement context. |
| `align(bounds, face, hit)` / `fits(level, pos, shape)` | Geometry alignment / occupancy checks used by planning. |
| `begin(plan)` / `commit(level, pos)` / `end()` | Internal pending-plan lifecycle. Do not overlap independent placements or leave a plan pending after failure. |
| `refresh(entity)` | Send a block update. This does not replace dirty marking or helper reconstruction. |

A plan exposes `state`, `pos`, `offset`, `shape`, and `valid` through accessors. Forge 1.16.5 uses a regular class for the plan; modern targets use a record. Client implementation classes under `client.placement` and mixins are implementation details; avoid calling them from common/server initialization.

## Version differences

| Target | Seat-height setter's first parameter | Other integration details |
| --- | --- | --- |
| Supported Fabric targets | Registered `Block` | Resolve after the block has been registered. |
| Forge 1.16.5, 1.19.2, 1.20.1 | `RegistryObject<Block>` | The setter records its ID without resolving the block early. |
| NeoForge 1.21.1 | `DeferredBlock<Block>` | Pass the holder, preserving deferred registration. |
| NeoForge 1.21.11, 26.1.2, 26.2 | `Identifier` | Pass the block ID, for example your holder's `getId()`, rather than resolving its block during static initialization. |

Further differences:

- Forge 1.16.5 uses names such as `World`, `BlockItemUseContext`, `Vector3d`, and `ActionResultType`, and emits Java 8 bytecode. Its default sitting surface and rider correction differ from modern targets.
- Modern examples use `Level`, `BlockPlaceContext`, `Vec3`, and `InteractionResult`. Newer targets also change registry-ID types and property construction, removal/interaction callbacks, and preview-rendering signatures.
- The helpers and mixins are already implemented for each supported target; Fabric 26.2 and 26.3 use one shared implementation with a cached seating compatibility adapter. Consumers do not need to port those internals; adapt their own block hooks and registration code.
- Do not broaden Minecraft version metadata merely because two source sets look similar. Use the exact published target or the documented universal Fabric pair; other combinations have not been validated.

## Compatibility and troubleshooting

| Symptom | Check |
| --- | --- |
| Missing required `eqlib` at launch | Install the universal runtime JAR on both sides and declare the loader metadata dependency. |
| Overlapping furniture packages | Register once; avoid parent/child prefixes or duplicate setup calls. |
| Block is not considered supported | Check its concrete Java class package. A registry namespace or superclass in the package does not opt in a different concrete class package. |
| Stale shapes or incorrect offsets | Register the adapter before block construction; preserve dynamic shape behavior and separate full raw geometry from shifted/local geometry. |
| `Decoration is missing placement storage` | Ensure every opted-in placement-capable block creates a registered block entity. |
| Deferred holder is unbound during startup | Keep helper lookup lazy; use the target's holder/ID seat-height overload rather than resolving blocks before registration. |
| Offset disappears after reload | Preserve superclass save/load calls, block entity registration, update-tag/packet handling, and stable saved IDs. |
| Orphan helpers or duplicate drops | Run the parent placement/removal hooks in the correct order and avoid independent helper drops or cleanup after discarding the old offset. |
| Seat mounts remain after dismount | Check that the library seat lifecycle mixin is loaded and parent-removal cleanup runs. |
| Helpers do not forward a custom interaction | Check the target's parent interaction overrides and the older interaction hook where available. |
| Dedicated server crashes loading client classes | Keep client-only classes out of common initialization and do not install a second copy of library mixins. |

The library preserves its existing placement NBT keys and offset-interface method names for saved-world compatibility. Let the library read and write this data rather than renaming or duplicating its storage keys. Keep your own parent/helper/block-entity registry IDs stable when migrating existing content.

When updating the library dependency, review API changes and adjust your supported release range and integration as needed.

## Validation checklist

Before distributing a consumer:

- Start a dedicated server and client with the matching published library JAR, including a run without a local library source checkout.
- Place, target, pick, interact with, and break parent and helper cells in each orientation; check creative and survival drops.
- Test precise placement on all six faces, blocked cells, world borders, and inventory blocks.
- Save/reload the world and unload a chunk containing precisely placed blocks; verify offsets, shapes, and helper ownership afterward.
- Test seating, dismount cleanup, parent removal, and seats on precisely placed blocks.
- Test alongside another eqlib consumer so package adapters, helper suppliers, and compatibility rules remain independent.

For build and publication commands see the [README](../README.md). For library-maintainer packaging and validation tools see the [universal JAR maintainer guide](UNIVERSAL.md#shared-logic-and-validation).

## Neutral clock utilities

`com.cookiecraftmods.eqlib.core.clock.ClockTime.hourAngle(long)` and `minuteAngle(long)` convert Minecraft day ticks into clockwise hand angles in degrees. They handle negative and large tick values without a game dependency.

`com.cookiecraftmods.eqlib.core.clock.ClockHandMesh.read(Reader, String)` reads a named hand element from a Blockbench-style model JSON. The result exposes immutable `vertices()`, `pivotZ()`, and `uprightRotation()`. Each `Vertex` provides `x/y/z`, `u/v`, and `nx/ny/nz` accessors. Coordinates and UVs are normalized from model pixels; vertices retain outward face winding. Missing hands or unknown face names throw `IllegalArgumentException`. The caller owns and closes the reader.

The input must be exported model JSON with an `elements` array, not an arbitrary `.bbmodel` project. The selected element requires `name`, three-component `from` and `to`, `rotation.origin`, and a `faces` object whose faces each have four-component `uv` arrays. For example:

```json
{
  "elements": [{
    "name": "minute_hand",
    "from": [7, 8, 7],
    "to": [9, 15, 8],
    "rotation": {"origin": [8, 8, 8]},
    "faces": {"north": {"uv": [0, 0, 2, 7]}}
  }]
}
```

Pass `"minute_hand"` as the second argument to `read`. Each face produces four vertices; coordinates are relative to `rotation.origin` and divided by 16. UVs are also divided by 16, so supply UVs in the 0–16 model convention. `pivotZ()` is the origin's Z coordinate divided by 16; `uprightRotation()` is the hand's angle from +Y toward +X, in degrees, computed from its center relative to its pivot. The parser does not apply element rotation angles, resolve textures, or load parent models. Your renderer handles those concerns and any conversion from quads to triangles.

Supply your own model, texture, render lifecycle, and Minecraft-specific renderer. The parser uses the game's Gson; model resources and registration belong to your mod.

```java
float hour = ClockTime.hourAngle(6000L);     // Noon: 0 degrees.
float minute = ClockTime.minuteAngle(6500L); // Half an hour: 180 degrees.
```

Import `ClockTime` from `com.cookiecraftmods.eqlib.core.clock`. Minecraft tick zero is 06:00; angles wrap automatically.
