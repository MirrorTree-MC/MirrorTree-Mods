MirrorTree Mods — Minecraft 26.1.2 客户端包

本包包含 LIStorm、Journey、Sync Sign Notice、ModpackUpdater 四个正式模组。
运行环境：Java 25、Minecraft 26.1.2、Fabric Loader 0.19.5+。
以下依赖须单独安装：
- Fabric API 0.155.3+26.1.2
- Trinkets Updated 4.0.1+26.1
- Pufferfish's Skills 0.19.2（26.1 Fabric）
- Pufferfish's Attributes 0.8.2+26.1
- Starry Skies 4.2.0+26.1

LIStorm 已合入 AnnoyingEffects、Spirit in Storm、MirrorSkills，须备份并
移走这三个独立旧模组。内置 Serenia 默认技能树；停服时将旧
world/datapacks/mirrortree-skill-tree 备份移到 datapacks 之外，避免覆盖
内置资源。保留其他自定义数据包和 Pufferfish 玩家进度；回滚时同时恢复
旧 JAR 与旧技能树。LIStorm 不提供 Minecraft 1.21.1 旧存档自动转换。

本包不包含世界、配置、玩家数据或数据库凭据，不导入卧室建筑。
公告模组已内嵌 MySQL Connector/J，数据库连接配置由管理员提供。
不要安装 sources、GameTest 或 bedroom-regression 验证 JAR 到正式实例。
包内 manifest.json 列出版本与依赖，SHA256SUMS 校验包内文件；ZIP 本身
的校验值见相邻 .zip.sha256 或同批 SHA256SUMS。

完整安装说明、源码与许可证：
https://github.com/MirrorTree-MC/MirrorTree-Mods

ModpackUpdater 的 serverUrl 与签名公钥由管理员配置。每次启动自动检查，
确认「更新并重启」后自动下载、备份、应用并重启，用户额外内容保留。
更新服务直接托管于自有服务器，配置示例与问答发布向导见源码文档。
