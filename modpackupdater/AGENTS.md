# ModpackUpdater maintenance

Follow the root AGENTS.md. Preserve `modpackupdater` and `config/modpackupdater.json`.

- Client initialization and UI must never block on network or disk downloads. HTTP content is accepted only after Ed25519 verification using the configured public key. Downloads use only the configured origin, bounded sizes and SHA-256.
- Never replace loaded JARs. Stage a complete verified plan, then launch the JDK-only `UpdateApplier` in a separate JVM which waits for the game process to exit. Do not add Minecraft, Fabric, Gson or other external dependencies to that helper.
- Match managed mods by their Fabric ID. Preserve unrelated mods. Confine all changes to explicit `mods`, `resourcepacks` or `config` paths, refuse symlinks and protect the updater trust configuration. Recheck old hashes before applying. Keep verified backups and a durable recovery journal; rollback on failure.
- The update host publishes immutable content and atomically switches a signed release pointer. Keep private signing keys, runtime endpoint configuration and hosted mod files outside Git and distribution ZIPs.
- Run `./gradlew :modpackupdater:build` and `python3 -m unittest discover -s modpackupdater/tools -p 'test_*.py'`. For integration into the shared package, also follow tools/AGENTS.md.
