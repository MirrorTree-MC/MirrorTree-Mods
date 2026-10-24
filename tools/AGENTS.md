# Build and distribution maintenance

Follow the root [AGENTS.md](../AGENTS.md). Python tooling requires 3.10+.

- Build/distribution changes: from the repository root run `./gradlew build :journey:validationJar` and `python3 tools/verify_distribution.py` (Windows: `gradlew.bat`, `python`). The validation JAR is compiled only; never install it in production.
- Package only with `./gradlew assembleDistribution`; use `-Ppython=<executable>` when needed. If only packaged documentation changes, regenerate the ZIPs without repeating builds or gameplay tests.
- `distribution.json` defines modules and installation sides. Obtain production artifact paths from Gradle task outputs, never a wildcard over `build/libs`. Extract versions and dependencies from each built `fabric.mod.json`.
- Client ZIP: LIStorm, Journey, Sync Sign Notice and ModpackUpdater. Server ZIP: LIStorm and Journey. Preserve licenses/notices, `manifest.json`, per-file `SHA256SUMS`, `update_mods.py` and adjacent `.zip.sha256`; exclude configs, worlds, sources, GameTests and the Journey probe. List external dependencies without downloading them automatically.
- `verify_distribution.py` checks resources, entrypoints/mixins, licenses, dependencies, production/probe boundaries and bytes against `../docs/skill-tree-source.json`. Preserve these checks and their input manifest.
- Changes to packaging/updating require `python -m unittest discover -s tools -p test_distribution.py` plus inspection of generated ZIPs. Keep `package_distribution.py`, `update_mods.py` and their tests identical to the standalone copies in MirrorTree-ExternalMods; neither checkout may require the other at build time.
- The updater previews by default, matches Fabric mod IDs, preserves unrelated JARs, backs up replaced files beside `mods` in `mod-update-backups`, and restores replacements on failure. Applying it to a real instance requires separately authorized deployment and a stopped instance; never put credentials or live configs into a package.

## Logging

- Build/package tooling writes development logs under the relevant build unit's `build/logs/`; create the directory before redirecting output. Do not create `*.log` at the repository or module root. Minecraft keeps its standard runtime `logs/` directory.
- Console output should identify each stage and its concise result. Retain detailed diagnostics in the log and print the failed stage plus its log path on failure; do not report success when a child command fails.
- Keep logs, test output and diagnostic manifests out of Git and release ZIPs. User-facing package checksums and version/dependency manifests remain distribution content.

User-facing installation/update commands belong in [INSTALLATION.md](../docs/INSTALLATION.md) and `dist/README-*.txt`.
