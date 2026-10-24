# Sync Sign Notice maintenance

Follow the root [AGENTS.md](../AGENTS.md). This module has only a client Fabric entrypoint/mixin; exclude it from server distributions.

- Publish complete immutable notice lists; rendering reads one snapshot. Serialize refreshes and keep JDBC off the render thread. Use the ordered bounded result query directly; empty results clear notices and disk cache.
- Preserve original sign data/back text, marker fallback, the `jellynews` query contract, `config/sync-sign-notice/config.json` and the 24-hour offline-cache format. Never commit database credentials or notice contents.
- From the root, `./gradlew :sync-sign-notice:build` packages the JDBC driver. For affected client behavior run `:sync-sign-notice:runClientGameTest`: actual sign mixin/markers, concurrent publication/rendering, empty results, fresh/expired cache, bounded SQL query and JDBC class loading.
- JDBC test connections are substitutes; client assertions do not establish production MySQL connectivity or database-index/query-plan performance. Keep runtime config, worlds and logs in ignored output.
