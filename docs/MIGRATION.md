# 三模组仓库迁移

本次将现有修复后的源码合入 `MirrorTree-MC/MirrorTree-Mods`，统一维护 `main` 分支。模块仍分别生成 JAR；仓库名不会变成新的游戏注册命名空间。

## 源码来源

通过非 squash 的子目录导入保留现有可用 Git 历史，原提交可在新仓库历史中追溯。

| 新目录 | 来源 | 导入提交 |
| --- | --- | --- |
| `listorm/` | [BaicaiBear/LIStorm](https://github.com/BaicaiBear/LIStorm) | `ce1b70fdf981ecc2e3fb5c4d9710761c671e3586` |
| `journey/` | [BaicaiBear/MirrorTree-Journey](https://github.com/BaicaiBear/MirrorTree-Journey) | `6bd354e5f6dd18778eba06b260dad220db9d64b2` |
| `sync-sign-notice/` | 用户提供的 ZIP 整理出的本地源码仓库 | `bf066f0048ceccb27884d38c34fb35f3b41a8c40` |

Journey 导入提交已经合并原 master 与 server 历史，原 server 提交 `b9bc5b4569e974861841d03e95ce1c636e08e10c` 仍可追溯。Sync Sign Notice 仅有从 ZIP 导入后建立的本地提交；本次没有恢复其原上游仓库历史。原独立仓库保留作为回溯来源，本次不修改或归档它们。MirrorSkills 不属于本次三个模块的合并范围。

## 兼容边界

| 模块 | 保留的 mod ID | 保留的版本 |
| --- | --- | --- |
| LIStorm | `lantern-in-storm` | `6.0.0-alpha.4+26.1.2` |
| Journey | `mirrortree` | `6.1.0+26.1.2` |
| Sync Sign Notice | `sync-sign-notice` | `1.1.0+26.1.2` |

方块、物品、效果、维度、网络消息、数据包及配置命名空间保持原有值。卧室仍为 `mirrortree:bedroom`；世界内 `lantern-in-storm/players/`、`mirrortree/dreams/` 及 `config/sync-sign-notice/` 的路径不因源码目录改变。

本次集中构建和发布配置，不重新迁移世界、不重置玩家、不改动服务器数据库设置。Sync Sign Notice 的元数据许可证按其原有 LICENSE 校正为 MIT。

## 构建与分发

根目录管理唯一 wrapper 和共用工具链，构建与校验在本地执行。Journey 通过 Gradle 项目依赖编译 LIStorm API，构建不再要求相邻的旧 LIStorm 仓库。各模块保持独立版本；发行包名称标识 Minecraft 目标版本，发布标签标识整批构建。

客户端包包含三个正式模组，服务端包只包含 LIStorm 与 Journey。第三方模组、正式服配置、公告数据、技能树数据包、世界和玩家存档均不随包分发。MySQL Connector/J 仍由公告模组按原有方式内嵌。

`build` 构建模块及发行包并执行已接入的自动检查；`assembleDistribution` 可单独打包。`tools/verify_distribution.py` 检查分发内容并生成 `build/distributions/SHA256SUMS`。GitHub Actions 工作流已移除；安装包和校验文件由本地生成，发布标签不触发自动发布。

## 验证范围

根目录构建中的 LIStorm GameTests 与分发校验用于验证此次构建整合。Journey 的 `:journey:validationJar` 只编译测试探针；该探针会改动测试世界与玩家并停止测试服务器，不能作为发行模组安装。

涉及公告行为的后续修改使用 `:sync-sign-notice:runClientGameTest`；涉及梦境流程的修改在一次性测试服务器副本中验证。编译或既有测试结果不代表本次已重启正式服、已完成真人连接测试或已连通正式 MySQL。
