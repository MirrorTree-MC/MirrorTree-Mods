# LIStorm maintenance

- Follow the root [AGENTS.md](../AGENTS.md). Current source home is https://github.com/MirrorTree-MC/MirrorTree-Mods/tree/main/listorm.
- Preserve the `lantern-in-storm` mod ID and the inherited AnnoyingEffects/Spirit in Storm registration and save IDs.
- Bedroom safety belongs here: `mirrortree:bedroom` has no environmental nightmares or protective-item durability consumption.
- Lamp placement still obeys building permissions. Keep Starry Skies activation/protection rules separate from placement permission.
- Keep common and client source sets separated. Journey consumes the public LIStorm API through a Gradle project dependency.
- From the repository root, use `./gradlew :listorm:build`; run `:listorm:runClientGameTest` or compatibility GameTests only when the affected behavior requires them.
