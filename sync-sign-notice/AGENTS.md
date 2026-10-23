# Maintenance

- Target Minecraft 26.1.2, Java 25, client-only Fabric entrypoint and client mixin.
- Publish complete immutable notice lists; rendering must read one snapshot. Serialize refresh requests and never read JDBC on the render thread.
- Empty database results clear current notices and cache. Preserve original sign data and back text.
- Do not commit config credentials, notice contents, run directories or build outputs.
- ./gradlew build packages the JDBC driver. ./gradlew runClientGameTest checks the actual client mixin, marker fallback, concurrent rendering/publication, empty database handling, fresh/expired cache, and JDBC class loading.
- The 2026-10-23 client test passed all nine checks. It does not itself prove connectivity to the production MySQL server.
