# opaleq's Library (`eqlib`)

Shared precise placement, helper-block footprints, seating, and clock utilities for Minecraft mods. Install `dist/eqlib-universal.jar` on both client and server. The same runtime file selects the adapter for the supported Minecraft version and loader.

See the [API guide](docs/API.md) for integration and the [universal JAR guide](docs/UNIVERSAL.md) for packaging and development setup.

## Supported targets

| Minecraft | Loaders |
| --- | --- |
| 1.16.5 | Forge |
| 1.19.2 | Forge |
| 1.20.1 | Fabric, Forge |
| 1.21.1 | Fabric, NeoForge |
| 1.21.11 | Fabric, NeoForge |
| 26.1.2 | Fabric, NeoForge |
| 26.2 | Fabric, NeoForge |
| 26.3 | Fabric |

This is the Modern Decorations support matrix. Other Minecraft versions and loaders are not advertised as compatible.

## 1.0.3

Seats now record their parent position and clean themselves up on their server-side entity ticks. Consumers no longer need a level-tick cleanup handler; the existing `removeEmptySeats` method remains compatible and visits only tracked seats. Parent removal respects seat ownership, including precisely placed furniture and neighboring seats. Seat-height setters reject non-finite values. Saved block IDs and placement NBT keys remain unchanged.

## Build and publish

```powershell
python tools/verify_boundaries.py
python tools/assemble_universal.py
python tools/assemble_universal.py --from-builds --publish
python tools/verify_universal_bundle.py
```

Python 3 and JDKs 17, 21, and 25 are required; Forge 1.16.5 emits Java 8 bytecode. The matrix discovers installed JDKs or accepts `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME`. The root Gradle `build` and `publishUniversal` tasks provide equivalent entry points. Logs and internal adapters are kept under ignored build directories.

The main Maven artifact is `com.cookiecraftmods:eqlib:${eqlib_version}`. Development API classifiers are `dev-<loader>_<minecraft>`. Set `eqlib_version` to your chosen published release. The local repository is `maven/`; no public repository or hosting service is configured.

Internal adapter projects remain under `versions/`. Fabric 26.2 and 26.3 use the single `versions/fabric_26.2-26.3` project and share its implementation and payload. Other adapters preserve their required mappings, loader APIs, and bytecode levels. Build an individual adapter with `python tools/build_matrix.py build --target fabric_1.21.1`; this does not assemble a new complete distribution.

## Ownership and world compatibility

Consumers own blocks, items, registry IDs, helper registrations, tags, resources, and content-specific behavior. Call `FurnitureApi.register` before constructing furniture blocks, then use the library's helper, footprint, precise-placement, and seating contracts. See the [API guide](docs/API.md) for complete examples.

Modern Decorations retains its existing saved IDs, helper properties, block entities, and placement NBT keys. Furniture models, inventories, beds, water effects, sound content, clock renderers, and historical content migrations remain in MDM. The neutral clock time conversion and hand-mesh parser live in eqlib's core and do not require MDM.
