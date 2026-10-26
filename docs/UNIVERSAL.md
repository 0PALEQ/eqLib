# Universal runtime JAR

`dist/eqlib-<mod_version>.jar` contains the adapters for every loader/game combination in the [supported matrix](../README.md#supported-targets). Install the same file on both client and server. Install one eqlib runtime per instance; remove superseded eqlib runtime files when updating.

?Universal? covers this explicit matrix. It does not promise compatibility with untested Minecraft releases or additional loaders. A consumer mod still needs its own compatible build for its game and loader.

## Architecture

The outer JAR contains a small bootstrap with no Minecraft API references, a Java 8 neutral core, and isolated adapter payloads. The neutral core is stored once. Public classes such as `FurniturePartBlock` inherit different Minecraft classes across releases, so their implementations remain in the selected adapter with the same API package names.

Fabric selects the adapter in a Mixin configuration plugin before resolving implementation mixins. It adds only the selected payload to the game class path. Forge uses its native early mod/dependency locator interfaces; NeoForge uses its native dependency discovery pipeline. The selected Forge/NeoForge payload is an ordinary loader-native mod with its own metadata and mixins. Inactive loader bridges and game implementations are never loaded into the game.

The selected payload is combined with the neutral core in a hash-addressed `.eqlib/` cache inside the run directory. Existing cache entries are verified before reuse; writes use temporary files and an atomic move where supported. Unsupported targets fail explicitly. The cache is generated data and can be removed while the game is closed.

Production adapters retain the mappings and reobfuscation of their individual loader builds. Development adapters retain named mappings. The bootstrap detects the environment; Forge development runs explicitly set `eqlib.development=true`. This property belongs in development runs only. Both sets are inside the same runtime download, so consumers do not need a separate development runtime.

Fabric 26.2 and 26.3 reuse one payload. Its cached seating compatibility handle resolves the invulnerability method available in the running game. Other incompatible game APIs use separate adapters; widening a dependency range is insufficient to merge them.

## Build and publish

```powershell
python tools/assemble_universal.py
python tools/verify_universal_bundle.py
python tools/verify_bootstrap.py
python tools/assemble_universal.py --from-builds --publish
```

The first command builds all internal adapters before packaging. `--from-builds` is for packaging already completed adapter outputs. Rebuild after source changes. `python tools/build_matrix.py build` and `publish` also assemble the universal distribution after the complete adapter matrix succeeds. Targeted builds only build the selected internal adapters.

The output is `dist/eqlib-<mod_version>.jar`, currently `dist/eqlib-1.0.3.jar`. The filename follows the canonical adapter's `mod_version`. Publication writes a main universal runtime and development API classifiers to the checkout's local `maven/` repository. Internal adapters are build inputs, not additional installation requirements.

## Develop a consumer

Use the main artifact at runtime and the matching named API classifier at compile time. For example, for a Mojang-mapped Fabric 1.21.1 build:

```groovy
repositories {
    maven { url = uri('path/to/opaleqsLibrary/maven') }
}
dependencies {
    compileOnly "com.cookiecraftmods:eqlib:${eqlib_version}:dev-fabric_1.21.1"
    runtimeOnly "com.cookiecraftmods:eqlib:${eqlib_version}"
}
```

Keep the runtime as an ordinary runtime dependency; do not send the entire universal JAR through Loom remapping or Forge reobfuscation. API classifiers are already named for their target's mappings and are development-only. They contain the implementation needed by standalone verification programs, but must not be installed in a game or shaded into a consumer JAR. An independently configured Yarn-mapped consumer must translate its compile API to its own mappings.

Forge/NeoForge early discovery must see the universal runtime in the development run's `mods/` directory before the JVM starts. Copy the resolved main artifact there with a Gradle `Copy` task and make the game run depend on that task. Set `eqlib.development=true` through your loader's run configuration. Never put the named API classifier in `mods/`. See Modern Decorations' `shared/gradle/eqlib.gradle` for ForgeGradle and ModDev examples. Fabric uses its ordinary runtime class path and automatically detects development mode.

Declare `eqlib` as required in the consumer's loader metadata; [API installation](API.md#install-the-dependency) shows the metadata entries. Keep your own Minecraft and Fabric API dependencies matched to your game. Neither the universal publication nor its compile API pulls a game-specific Fabric API into your mod.

## Shared logic and validation

`shared/core` contains Java 8 utilities without Minecraft references. Clock timing and model-hand mesh parsing are shared with MDM; model resources and renderers remain consumer-owned. Compatible adapter sources are shared where their APIs permit it, while source groups identify exactly which targets compile each implementation.

`tools/verify_universal_bundle.py` checks the payload matrix, metadata, core uniqueness, bytecode levels, and development API outputs. `tools/verify_bootstrap.py` additionally runs extraction, isolated core linkage, corrupt-cache repair, and unsupported-target checks on Java 8 (set `JAVA8_HOME` if needed). `tools/verify_boundaries.py` checks source independence and mixin ownership. These checks complement real game runs; they do not prove runtime mixin or rendering compatibility by themselves.

The Fabric 26.2/26.3 integration fixture is `tests/universal-fabric-runtime.gradle`. Applied with `-I` to an MDM target, it verifies placements, saved offsets and packets, seating, and clean server shutdown. With `-PuniversalClient` it also opens a prepared world and captures valid/blocked placement previews. Other loader/game regressions use MDM's isolated placement unload tests and its legacy smoke fixture. Generated logs and result markers are kept under ignored `build/` directories.

From the Modern Decorations checkout, reproduce the complete server matrix with:

```powershell
python tools/verify-eqlib-runtime.py --offline
```

Omit `--offline` when dependencies are not cached. The runner keeps a checksum-locked artifact throughout all targets and writes per-target logs and a report under `build/eqlib-runtime/`. Use `python tools/verify-eqlib-matrix.py` for complete consumer builds and standalone regression checks. Data-generation run graphs also install the universal runtime before launch.

After the matrix, bootstrap, and client checks pass, run `python tools/record_universal_validation.py --consumer "<MDM-checkout>"` from the library checkout to record the current artifact checksum and evidence in `dist/universal-validation.json`. The report distinguishes development game runs from production adapter builds.
