# Maintainer guide: universal runtime JAR

**Audience: eqlib maintainers and contributors developing the library itself.** This guide covers the universal runtime architecture, building, publishing, and release validation. If you are using eqlib in your own mod, follow the [API guide](API.md), including its [dependency and development-run setup](API.md#install-the-dependency). You do not need to modify the library source to integrate its public features.

`dist/eqlib-<mod_version>.jar` contains the adapters for every loader/game combination in the [supported matrix](../README.md#supported-targets). Install the same file on both client and server. Install one eqlib runtime per instance; remove superseded eqlib runtime files when updating.

"Universal" covers this explicit matrix. A consumer mod still needs its own compatible build for its game and loader.

## Architecture

The outer JAR contains a small bootstrap with no Minecraft API references, a Java 8 neutral core, and isolated adapter payloads. The neutral core is stored once. Public classes such as `FurniturePartBlock` inherit different Minecraft classes across releases, so their implementations remain in the selected adapter with the same API package names.

Fabric selects the adapter in a Mixin configuration plugin before resolving implementation mixins. It adds only the selected payload to the game class path. Forge uses its native early mod/dependency locator interfaces; NeoForge uses its native dependency discovery pipeline. The selected Forge/NeoForge payload is an ordinary loader-native mod with its own metadata and mixins. Inactive loader bridges and game implementations are never loaded into the game.

The selected payload is combined with the neutral core in a hash-addressed `.eqlib/` cache inside the run directory. Existing cache entries are verified before reuse; writes use temporary files and an atomic move where supported. Unsupported targets fail explicitly. The cache is generated data and can be removed while the game is closed.

Production adapters retain the mappings and reobfuscation of their individual loader builds. Development adapters retain named mappings. The bootstrap detects the environment; Forge development runs explicitly set `eqlib.development=true`. This property belongs in development runs only. Both sets are inside the same runtime download, so consumers do not need a separate development runtime.

Fabric 26.2 and 26.3 reuse one payload. Its cached seating compatibility handle resolves the invulnerability method available in the running game. Other incompatible game APIs use separate adapters; widening a dependency range is insufficient to merge them.

## Build and publish

Run these commands from the library checkout on Windows. Install Python 3 and JDKs 17, 21, and 25 first; set `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME` if automatic discovery cannot find them. The bootstrap verification command additionally requires JDK 8 (`JAVA8_HOME`). These are library-build prerequisites, not a requirement for every consumer to build the entire matrix on each development run.

```powershell
python tools/verify_boundaries.py
python tools/assemble_universal.py
python tools/verify_universal_bundle.py
python tools/verify_bootstrap.py
```

The assembly command builds all required adapters and packages them into one universal runtime JAR. Rebuild after source changes. Distribute only `dist/eqlib-<mod_version>.jar`; users do not select or install individual adapters.

To also publish the universal runtime and development API classifiers to the local Maven repository, run:

```powershell
python tools/assemble_universal.py --publish
```

The output is `dist/eqlib-<mod_version>.jar`. The filename follows `mod_version` in `versions/fabric_26.2-26.3/gradle.properties`. Publication writes a main universal runtime and development API classifiers to the checkout's local `maven/` repository. Internal adapters are build inputs, not additional installation requirements.

Publish both the main runtime artifact and the development API classifiers for mod developers. The Fabric 26.2/26.3 pair publishes separate `dev-fabric_26.2` and `dev-fabric_26.3` classifiers despite sharing an implementation. Consumer configuration is documented in the [API installation guide](API.md#install-the-dependency).

## Shared logic and validation

`shared/core` contains Java 8 utilities without Minecraft references. Clock timing and model-hand mesh parsing are available to every consumer; your mod supplies model resources and renderers. Compatible adapter sources are shared where their APIs permit it, while source groups identify exactly which targets compile each implementation.

`tools/verify_universal_bundle.py` checks the payload matrix, metadata, core uniqueness, bytecode levels, and development API outputs. `tools/verify_bootstrap.py` additionally runs extraction, isolated core linkage, corrupt-cache repair, and unsupported-target checks on Java 8 (set `JAVA8_HOME` if needed). `tools/verify_boundaries.py` checks source independence and mixin ownership. These checks complement real game runs; they do not prove runtime mixin or rendering compatibility by themselves.

Before releasing the library, validate a consumer test mod in a client and a dedicated server for every supported target. Test normal and precise placement, blocked cells, helper interactions and removal, seating and dismount cleanup, and saved offsets after world reload and chunk unload. Check both valid and blocked placement previews on the client. See the [API validation checklist](API.md#validation-checklist) for details.

The fixtures under `tests/` support integration testing but require a consumer test project with matching block registrations and run configuration; they are not standalone tests for an arbitrary mod. Generated logs and result markers belong under ignored `build/` directories.

`tools/record_universal_validation.py --consumer "<consumer-checkout>"` records evidence for the current artifact in `dist/universal-validation.json`. It requires the specific build reports, runtime markers, and client results expected by that script. Use it only with a test project implementing that report layout; it does not run the tests itself.
