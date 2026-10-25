# 安装与升级

目标为 Minecraft 26.1.2、Java 25、Fabric Loader 0.19.5+。客户端与服务端应使用同一批 LIStorm 和 Journey JAR；Sync Sign Notice 和 ModpackUpdater 仅装客户端。

## 发行包与依赖

- `MirrorTree-Mods-26.1.2-client.zip`：LIStorm、Journey、Sync Sign Notice、ModpackUpdater。
- `MirrorTree-Mods-26.1.2-server.zip`：LIStorm、Journey。

正式包位于发布产物的 `build/distributions/`，包含 `mods/`、许可说明、版本/依赖清单 `manifest.json`、逐文件 `SHA256SUMS` 和更新脚本。ZIP 自身的校验值见相邻 `.zip.sha256` 或同批 `SHA256SUMS`。不包含配置、世界、玩家数据、数据库凭据或测试模组。

以下第三方依赖需单独安装到两端：

| 依赖 | 对应版本 | 使用模块 |
| --- | --- | --- |
| Fabric API | 0.155.3+26.1.2 | 全部 |
| Trinkets Updated | 4.0.1+26.1 | LIStorm |
| Pufferfish’s Skills | 0.19.2（26.1 Fabric） | LIStorm |
| Pufferfish’s Attributes | 0.8.2+26.1 | LIStorm |
| Starry Skies | 4.2.0+26.1 | Journey |

公告模组已内嵌 MySQL Connector/J。Farmer’s Delight、Biomes O’ Plenty、Mythic Metals 是 LIStorm 可选兼容；使用它们时还需安装其自身依赖。

## 更新 JAR

先解压对应发行包。自动更新需要 Python 3.10+；在解压目录预览客户端变更：

```sh
python update_mods.py --side client --mods "<实例的mods目录>"
```

服务端改用 `--side server`。确认目标路径后，关闭目标客户端或通过既有管理方式停服并等待保存，再追加 `--apply` 执行。脚本按 mod ID 识别旧版本，保留无关 JAR，将替换文件备份到 `mods` 同级的 `mod-update-backups`；安装失败会恢复已替换文件。也可停实例后手动备份、移走同 ID 的旧 JAR，再合并新 `mods/`。更新后需要重启。

LIStorm 已合入 AnnoyingEffects、Spirit in Storm 和 MirrorSkills；升级时备份并移走这三个独立旧模组，避免重复注册。不要删除 Pufferfish Skills / Attributes 或其玩家进度。

Journey 6.2.0 起，首次右键卧室床可选择随机入梦或加入聚落。服务端安装 Essential Commands 0.39.0-mc26.1.1 或兼容新版后，管理员可在主世界用 `/mtwarp 名称` 创建聚落，用 `/mtwarp-delete 名称` 删除；聚落菜单只列出玩家可访问、名称以 `聚落：` 开头的主世界 warp。未安装 Essential Commands 时仍可随机入梦。详细规则见 [Journey 聚落管理](../journey/README.md#聚落管理)。

## 自托管客户端更新

ModpackUpdater 每次启动后台检查，仅有待更新内容时，主菜单才自动显示确认页；无更新时直接保留主菜单，自动重启成功只显示短暂完成通知。确认「更新并重启」后下载、验签和校验，独立进程等待游戏退出再备份、替换，成功后自动启动游戏；也可稍后更新。旧文件留存在实例 `mod-update-backups`，用户额外内容保留。客户端需配置自有服务器 URL 与固定签名公钥；公开发行包不包含生产配置或密钥。独立服务、问答发布向导、端口转发和恢复命令见 [ModpackUpdater 使用说明](../modpackupdater/README.md)。

## 默认技能树

LIStorm 内置 Serenia 的 `mirrortree:combat`（16 节点）和 `mirrortree:support`（42 节点），辅助树使用 LIStorm 的经验来源。技能 ID、原 `mirrorskills` 命令、冷却、按键和附件标识保持；默认技能按键为 I 隐身、O 冲刺、反引号无敌。

停服并备份世界中的 `datapacks/mirrortree-skill-tree`，将这个旧副本移到 datapacks 之外；同路径外部资源会覆盖内置技能树，其他自定义数据包保留。重启后检查原有技能进度、冷却和 HUD。回滚时先停服，同时恢复原 JAR 与原外部技能树。

## 数据与兼容边界

- 本仓库整理和同版更新不重置玩家或重命名数据路径。LIStorm 使用世界内 `lantern-in-storm/players/<UUID>.json` 与 `config/AnnoyingEffects.json`；Journey 使用世界内 `mirrortree/dreams/<UUID>.json`；公告模组使用 `config/sync-sign-notice/`。
- LIStorm 不提供 Minecraft 1.21.1 旧 NBT/JSON 存档或配置的自动转换。Journey 另保留旧 `config/mirrortree/dream_data.json` 坐标记录读取，且不删除旧文件；这不代表整套旧世界迁移能力。
- 技能药水标记与隐藏效果链会继续持久化，无限效果保持无限；历史上已经丢失的标记无法可靠推断，不自动回写旧药水数值。
- 卧室沿用实例已有配置，本包不导入卧室建筑。数据库连接由管理员配置。游戏行为和可选兼容细节见各模块 README。
- LIStorm 仍为 alpha；长期玩家体验、真实多人网络压力和任意第三方版本组合不在自动检查的保证范围内。
