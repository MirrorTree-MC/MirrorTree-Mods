# 源码来源与许可

仓库保留三个模块的可用 Git 历史，各模块继续使用原 mod ID；仓库名称不是新的游戏注册命名空间。

| 模块 | 来源 | 导入提交 |
| --- | --- | --- |
| LIStorm | [BaicaiBear/LIStorm](https://github.com/BaicaiBear/LIStorm) | `ce1b70fdf981ecc2e3fb5c4d9710761c671e3586` |
| Journey | [BaicaiBear/MirrorTree-Journey](https://github.com/BaicaiBear/MirrorTree-Journey) | `6bd354e5f6dd18778eba06b260dad220db9d64b2` |
| Sync Sign Notice | 用户提供 ZIP 后建立的本地源码仓库 | `bf066f0048ceccb27884d38c34fb35f3b41a8c40` |

Journey 的 master/server 历史已合并，原 server 提交 `b9bc5b4569e974861841d03e95ce1c636e08e10c` 可追溯。公告模组只有 ZIP 导入后建立的本地历史，未恢复其原上游仓库历史。

LIStorm 合并内容的基线：Lantern in Storm 1.21.1 / 5.0.0（`3dff58e` 加当时本地改动）、Spirit in Storm 2.0.1（`42b7175` 加当时本地改动）、AnnoyingEffects `ui` 1.2.2（`324b72e`）。本地 Lantern/Spirit 的未提交实现和资源也是来源的一部分，不能仅用这些上游提交替换当前源码。Spirit 使用探索经验设计，不是旧版灵魂奖励或已删除的胡萝卜铁砧彩蛋。

MirrorSkills 的可用源提交 `3057c270b9a1612f85cca34cb002d1d37f162859` 作为合并父历史保留，内容位于 LIStorm，未新增独立构建模块。保留原包名、技能/参数与持久化标识；许可为 CC0 1.0。

默认技能树来自 `Serenia/Journey/datapacks/mirrortree-skill-tree`，2026-10-23 导入的 11 份 JSON 及 Unlicense 原文按 [skill-tree-source.json](skill-tree-source.json) 校验。未采用 Astreet 副本，也未导入独立数据包的 mod 元数据或 `pack.mcmeta`。类别/节点 ID 属于玩家进度契约，升级规则见 [INSTALLATION.md](INSTALLATION.md)。

各模块许可证和原文链接统一见 [LICENSES.md](../LICENSES.md)。AnnoyingEffects 与公告模组采用所附 MIT 文本，MirrorSkills 为 CC0 1.0，默认技能树为 Unlicense；旧元数据中的不同标注不替代原许可证。内嵌或外置第三方依赖仍遵循各自许可证。
