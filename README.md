# opaleq's Library (`eqlib`)

Shared precise placement, helper-block footprints, seating, and clock utilities for Minecraft mods. Install `dist/eqlib-<mod_version>.jar` on both client and server. The same runtime file selects the adapter for the supported Minecraft version and loader.

Using eqlib in your mod? See the [API guide](docs/API.md) for dependency setup and feature integration. Developing or maintaining eqlib itself? See the [universal JAR maintainer guide](docs/UNIVERSAL.md) for architecture, building, publishing, and release validation.

For mod authors, start with [choosing an integration path](docs/API.md#choose-an-integration-path), then [install the dependency](docs/API.md#install-the-dependency). The guide covers [multicell furniture](docs/API.md#integrate-multicell-furniture), [precise placement](docs/API.md#enable-precise-placement), [seating](docs/API.md#add-seating), and [clock utilities](docs/API.md#neutral-clock-utilities).

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

Use a consumer mod built for your exact Minecraft version and loader. The universal library does not make a consumer mod compatible with other targets.

## Build and publish

Build and verify the universal distribution:

```powershell
python tools/verify_boundaries.py
python tools/assemble_universal.py
python tools/verify_universal_bundle.py
```

Distribute `dist/eqlib-<mod_version>.jar`. The assembly command builds all required adapters and packages them into that single runtime JAR.

To also publish the universal runtime and development API classifiers to the local `maven/` repository, use `python tools/assemble_universal.py --publish`.

The packaging tools currently invoke Windows Java executables (`java.exe`/`javac.exe`); use Windows with Python 3 and JDKs 17, 21, and 25. Forge 1.16.5 emits Java 8 bytecode. The matrix discovers installed JDKs or accepts `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME`. The root Gradle `build` and `publishUniversal` tasks wrap assembly and publication respectively; they do not run the separate verification scripts. Logs and internal adapters are kept under ignored build directories.

The main Maven artifact is `com.cookiecraftmods:eqlib:${eqlib_version}`. Development API classifiers are `dev-<loader>_<minecraft>`. Set `eqlib_version` to your chosen published release. The local repository is `maven/`; no public repository or hosting service is configured.

## Integrate the library

Consumers own blocks, items, registry IDs, helper registrations, tags, resources, and content-specific behavior. Call `FurnitureApi.register` before constructing furniture blocks, then use the library's helper, footprint, precise-placement, and seating contracts. See the [API guide](docs/API.md) for complete examples.

Your mod supplies models, resources, inventories, and renderers. Preserve your existing block/helper IDs and placement data when updating an integration.
