# MirrorTree Mods

MirrorTree 自制模组的统一源码仓库，面向 Minecraft 26.1.2 / Fabric / Java 25。三个模组统一构建，保留各自的模组 ID、版本和存档路径。

| 目录 | 内容 | 安装端 |
| --- | --- | --- |
| [listorm](listorm/) | 灯具防护、梦魇效果、探索经验 | 客户端与服务端 |
| [journey](journey/) | 卧室、入梦往返、物品与装饰器 | 客户端与服务端 |
| [sync-sign-notice](sync-sign-notice/) | MySQL 公告标牌 | 仅客户端 |

在仓库根目录使用 JDK 25 构建：

```sh
./gradlew build
python3 tools/verify_distribution.py
```

Windows 使用 `gradlew.bat` 和 `python`。发行包位于 `build/distributions/`：

- `MirrorTree-Mods-26.1.2-client.zip`：三个模组的正式 JAR。
- `MirrorTree-Mods-26.1.2-server.zip`：LIStorm 与 Journey 的正式 JAR。
- `SHA256SUMS`：校验脚本生成的 ZIP 校验值。

发行包中的 `mods/` 合并到对应实例的模组目录；先移走同 ID 的旧版本。Fabric API、Trinkets Updated、Pufferfish’s Skills、Starry Skies 等第三方依赖仍需单独安装，具体版本见包内说明。现有世界、技能树数据包和数据库连接配置不会随包分发。

维护入口：[AGENTS.md](AGENTS.md)。历史与兼容边界：[迁移记录](docs/MIGRATION.md)。各模块许可证：[LICENSES.md](LICENSES.md)。
