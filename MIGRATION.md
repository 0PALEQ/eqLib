# Modern Decorations migration

The library owns precise placement, persistent/render offsets, helper footprints, multicell contracts, seating, and neutral clock timing/mesh utilities. Consumer package registrations replace direct MDM references. Each consumer retains its own registered content, helper IDs, block entities, NBT compatibility, models, and loader lifecycle.

The runtime distribution is now one [universal JAR](docs/UNIVERSAL.md). Internal adapters preserve incompatible Minecraft APIs; Fabric 26.2/26.3 share a payload. MDM compiles against named target APIs and loads the universal runtime through `shared/gradle/eqlib.gradle`. Source and composite dependencies have been replaced to prevent duplicate runtime mods.

Clock timing and mesh parsing moved into `com.cookiecraftmods.eqlib.core.clock`. MDM imports the shared implementation; its obsolete DTO copies were removed. Clock models/renderers, furniture-specific geometry, water effects, inventories, beds, sounds, and historical ID migrations remain in MDM because they describe its content.

Build and verification commands are documented in [README](README.md), [API](docs/API.md), and [universal packaging](docs/UNIVERSAL.md). Generated validation outputs are kept under ignored build directories. No public hosting service is configured.
