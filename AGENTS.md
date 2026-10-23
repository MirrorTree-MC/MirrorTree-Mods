# Maintenance

- Repository: https://github.com/MirrorTree-MC/MirrorTree-Mods. Maintain one `master` branch; the three modules keep their independent mod IDs and versions.
- Target Minecraft 26.1.2, Fabric Loader 0.19.5+, Java 25 and the root Gradle 9.7.1 wrapper. Build from the repository root; module-specific rules are in each module's AGENTS.md.
- Keep Journey's LIStorm dependency inside this Gradle build. Do not restore sibling-checkout paths, committed dependency JARs or separate module wrappers.
- Preserve registry/resource IDs, network IDs, configuration locations and world/player save formats. Repository consolidation must not reset player progress or change gameplay.
- Run `./gradlew build :journey:validationJar` and `python3 tools/verify_distribution.py` for build/distribution changes. Run additional targeted tests only for changed behavior; distinguish compilation, GameTests, client tests and real-server evidence.
- `./gradlew assembleDistribution` creates separate client/server ZIPs. Select production JAR task outputs explicitly. Never distribute sources JARs, GameTest mods or the Journey validation probe.
- `:journey:validationJar` only compiles a destructive isolated-server probe. It changes blocks/player data and stops its test server; never install or run it in production.
- Review the diff before committing and pushing. Preserve unrelated work and available source histories; do not force-push. Keep credentials, runtime configs, worlds, caches and build outputs out of Git.
- GitHub Actions builds on master/PRs/manual dispatch; pushing a `v*` tag publishes the verified ZIPs and SHA256SUMS. Creating a release tag is an explicit release action, not part of routine commits.
- Back up replaced runtime JARs before any separately requested deployment. Restart is required to load new JARs. Do not change production player data or stop production during source-only maintenance.
