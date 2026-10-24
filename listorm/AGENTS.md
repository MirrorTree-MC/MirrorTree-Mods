# LIStorm maintenance

Follow the root [AGENTS.md](../AGENTS.md). `LanternInStorm` coordinates common registration/events; `StormClient` owns client startup. Keep `lanterninstorm/{content,safety,progression,effects,network}`, AnnoyingEffects and MirrorSkills responsibilities distinct. Journey consumes the public API through its Gradle project dependency.

## Compatibility and behavior

- Preserve `lantern-in-storm`, inherited AnnoyingEffects/Spirit registration order/IDs, config keys and player formats. Keep the `LanternInStormAPI` entrypoints and dimension-aware spawn coordinates. No 1.21.1 data fixer is provided; provenance is in [UPSTREAM.md](../docs/UPSTREAM.md).
- `mirrortree:bedroom` is always safe: clear existing environmental nightmares on entry/login, suppress nightmare application and defense durability consumption. Placement obeys building permission in every dimension; keep Starry Skies activation/protection rules separate from placement.
- Lamp lookup visits only nearby loaded chunks; maintain the index on load/unload/removal without generating chunks. A safe dimension may still need nearby-lamp results for charging. Preserve radii, grace periods and effect/random-call order when optimizing.
- Defense lookup checks normal then cosmetic `offhand/glove` slots, keeps the last matching entry, then falls back to main/off hand; consumption/replacement must target the selected slot. Exhausted protection outside vanilla dimensions must retain debuffs until recharged.
- `AnnoyingStatusEffect`, `TanglingDreamsStatusEffect` and the deferred effect queue have behavior roles. Preserve each effect's independent registration, modifiers and config defaults; do not modify active-effect collections during iteration.
- Spirit uses food `10/max(1,count)+1`, first-biome 30, goal/challenge 10/50 and diminishing ore experience. Persist counts/deduplication; do not restore the removed carrot-anvil reward or double-count Farmer's Delight food callbacks.
- Keep client classes in the client source set. Missing optional material mods must keep fallback textures and conditional recipes; retain all upstream license notices.

## Skills and effect persistence

- Preserve skill/parameter/command names, `mirrorskills:skill_data`, payload IDs, cooldowns and marker names. `mirrortree:combat`/`mirrortree:support` category and node IDs are persistent progress. Keep `../docs/skill-tree-source.json` and imported JSON bytes; never substitute the unrelated Astreet tree.
- Copy attachments before mutation; publishing the same mutated object can suppress Fabric synchronization. Tick helpers run in original order without short-circuiting and publish all same-tick changes together. Do not reduce timer synchronization without changing and verifying its contract.
- Skill snapshots use copy-on-write maps and the compact network codec from alpha.6; update client and server together across that boundary. Keep full snapshots, tracking recipients and per-tick timer synchronization. Persistent NBT continues to use the legacy codec and keys.
- Duration/amplifier markers follow the values actually accepted by vanilla updates, including false; rejected updates retain existing markers. Preserve each hidden-effect layer through update, promotion, copy and codecs. Missing legacy markers read false; do not infer or rewrite already-lost historical bonuses.
- Keep infinite duration (`-1`) unchanged while applying amplifier rules; finite-duration formulas stay unchanged. Death flashback preserves its record on invalid/nonfinite/unloaded destinations or failed `teleportTo`; only a legacy null dimension falls back to the current world, and only success clears/publishes a copied record.

## Targeted verification

Run from the repository root unless stated otherwise:

- `./gradlew :listorm:build`: compilation and wired checks; `:listorm:checkSkillData` covers codec/copy isolation, and `:listorm:runGameTest` covers server state, skills, hidden effects and death/respawn/flashback.
- `:listorm:runClientGameTest` covers actual input/network/HUD data, equipment/lamps/effects/progression and temporary-world reopen. Use it for affected client behavior; assertions do not establish pixel fidelity or real network stress coverage.
- Optional compatibility: append `-PfarmersDelightTestJar=<absolute-jar>` or `-PcompatTestDir=<absolute-directory>` containing compatible mods and their dependencies. Reference fixture hashes remain in [COMPAT_FIXTURES.json](docs/COMPAT_FIXTURES.json).
- Resource edits: from `listorm/`, run `python3 tools/audit_resources.py`; root distribution verification also checks skill-tree source bytes. `:listorm:genSources` is available when inspecting local Minecraft APIs.
- Keep fixtures under `src/gametest`, logs/worlds under ignored build output. The [experience example](examples/spirit-experience-datapack/README.md) is a usable separate datapack, not disposable test output.
