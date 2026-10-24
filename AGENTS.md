# Maintenance

Repository: https://github.com/MirrorTree-MC/MirrorTree-Mods. Keep one root Git repository on `main`, one Gradle wrapper, and independently versioned modules. Journey consumes LIStorm through a project dependency; never restore sibling-checkout paths or committed dependency JARs.

- Target Minecraft 26.1.2, Fabric Loader 0.19.5+, Java 25 and the checked-in Gradle 9.7.1 wrapper. Run Gradle from this root.
- Preserve mod/resource/network IDs, config locations and world/player formats. Source maintenance does not authorize deployment, world changes or production restarts. Preserve unrelated work and available source history; do not force-push.
- Use targeted checks for changed behavior. Distinguish compile, GameTest, client, isolated-server and production evidence; static complexity analysis does not establish measured performance gains.
- Build and publish locally; do not add GitHub Actions. Keep credentials, runtime configs, worlds, caches and build outputs out of Git. Development logs belong in the relevant build unit's `build/logs/`, never root/module-root `*.log`; retain Minecraft's own runtime log locations.
- README files describe user behavior; `docs/` retains installation, provenance and required verification inputs. Put development rules in the applicable AGENTS.md; do not accumulate migration plans, audit transcripts or test-run diaries.

Read only the relevant maintenance entry:

- [LIStorm](listorm/AGENTS.md): lamps, safety, effects, progression and skills.
- [Journey](journey/AGENTS.md): bedroom/dream travel and the isolated validation probe.
- [Sync Sign Notice](sync-sign-notice/AGENTS.md): client notices and JDBC/cache behavior.
- [Distribution tools](tools/AGENTS.md): shared build, packaging, updater and release verification.
- [Documentation](docs/AGENTS.md): user documentation and preserved source manifests.
