# Sync Sign Notice — 26.1.2

Fabric 客户端模组（Java 25）。仅安装到客户端，不需要服务端 JAR。右键编辑标牌，使第一行为 [Notice]、第二行为公告序号、第四行为 SyncSign；显示公告不会改写标牌存档。

保留原 MySQL jellynews 查询、config/sync-sign-notice/config.json 和 24 小时离线缓存格式。数据库连接配置不包含在源码或 JAR 中。

编译：./gradlew build
客户端验证：./gradlew runClientGameTest

维护规则见 [AGENTS.md](AGENTS.md)。
