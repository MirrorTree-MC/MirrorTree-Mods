# Sync Sign Notice · 26.1.2

Fabric 客户端公告模组，mod ID 为 `sync-sign-notice`。仅安装客户端，MySQL Connector/J 已内嵌；依赖和发行包见[安装说明](../docs/INSTALLATION.md)。

编辑标牌时，将第一行设为 `[Notice]`、第二行设为公告序号、第四行设为 `SyncSign`。显示公告不会改写标牌存档或背面文本。

使用原 MySQL `jellynews` 查询与 `config/sync-sign-notice/config.json`，支持 24 小时离线缓存。数据库连接由实例管理员配置，源码和发行包不包含连接凭据。

开发维护见 [AGENTS.md](AGENTS.md)，许可见 [LICENSE](LICENSE)。
