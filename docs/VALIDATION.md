# Validation — 2026-10-23

Java 25 compilation passed. The dedicated-server regression probe passed 94 assertions with the deployed mod set, including MirrorSkills:

- Both real dimension identifiers loaded.
- Already-safe login state clears persisted nightmares; doll and talisman durability remains unchanged in bedroom.
- Every registered lamp item can be placed in both dimensions by a creative administrator (33 items per dimension; the unregistered white-paper block is checked via placement state).
- Ordinary Starry Skies lamps still cannot activate protection; metal lamp activation and safety remain functional.
- Exhausted dolls retain debuffs over five safety intervals; recharging restores cleansing.
- /wakeup rejects nonsleeping players; sleeping players move to bedroom adventure mode.
- The actual bed callback returns the player to the saved overworld location and survival mode.
- Effect copies and health/hunger/saturation/exhaustion survive disk round-trip.
- Ordinary bedroom interaction and item-drop restrictions remain enforced.
- First random dream queues and completes, registers the LIStorm origin, and preserves global bedroom spawn.

The probe uses an embedded network player in an isolated copy, not a human GUI session. It self-stops the test server. Existing old-world biome/structure warnings and a Polymer embedded-player packet-context warning occurred; these are not claimed fixed here. EasyAuth/whitelist were disabled only in the test instance.
