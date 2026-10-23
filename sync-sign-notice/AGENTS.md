# Sync Sign Notice maintenance

- Follow the root [AGENTS.md](../AGENTS.md). This module has only a client Fabric entrypoint and client mixin; exclude it from the server distribution.
- Publish complete immutable notice lists; rendering must read one snapshot. Serialize refresh requests and never read JDBC on the render thread.
- Empty database results clear current notices and cache. Preserve original sign data and back text.
- Do not commit database credentials, notice contents, runtime config, run directories or build outputs.
- From the repository root, `./gradlew :sync-sign-notice:build` packages the JDBC driver. `:sync-sign-notice:runClientGameTest` checks the actual client mixin, marker fallback, concurrent rendering/publication, empty database handling, fresh/expired cache, and JDBC class loading.
- Client tests do not establish connectivity to the production MySQL server.
