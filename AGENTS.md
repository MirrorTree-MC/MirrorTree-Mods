# Maintenance

- Target Minecraft 26.1.2, Fabric Loader 0.19.5+, Java 25. Keep one master branch combining client content and server dream travel.
- Preserve existing item, decorator, mixin and save identifiers. Never reset production player data as part of an upgrade.
- Bedroom restrictions apply to ordinary players; creative administrators may build. LIStorm owns nightmare safety and lamp behavior.
- All world reads, teleportation and player changes run on the server thread. First-dream search is bounded; load a candidate chunk before querying its height.
- Capture effect copies and persist the dream snapshot before leaving the overworld. Preserve global bedroom spawn independently from individual dream respawns.
- Build LIStorm alpha.4 in ../LIStorm, then run ./gradlew build validationJar here.
- validationJar is an isolated-server probe. It changes test blocks/player data and stops its test server. NEVER install it in production.
- Run the probe only against a disposable copy with bedroom and Starry Skies present, and authentication disabled for the embedded test player. Read bedroom-fix-regression.json.
- Source commits must exclude credentials, local libraries, caches, worlds and JAR outputs. Production deployment requires backups and a restart.
