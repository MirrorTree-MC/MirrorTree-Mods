# Journey maintenance

- Follow the root [AGENTS.md](../AGENTS.md). Keep client content and server dream travel in this module on the shared master branch.
- Preserve existing item, decorator, mixin and save identifiers. Never reset production player data as part of an upgrade.
- Bedroom restrictions apply to ordinary players; creative administrators may build. LIStorm owns nightmare safety and lamp behavior.
- All world reads, teleportation and player changes run on the server thread. First-dream search is bounded; load a candidate chunk before querying its height.
- Capture effect copies and persist the dream snapshot before leaving the overworld. Preserve global bedroom spawn independently from individual dream respawns.
- Build from the repository root with `./gradlew :journey:build :journey:validationJar`; LIStorm is built through the project dependency.
- `validationJar` is an isolated-server probe. It changes test blocks/player data and stops its test server. NEVER install it in production or include it in distribution ZIPs.
- Run the probe only against a disposable copy with bedroom and Starry Skies present, and authentication disabled for the embedded test player. Read `bedroom-fix-regression.json`.
