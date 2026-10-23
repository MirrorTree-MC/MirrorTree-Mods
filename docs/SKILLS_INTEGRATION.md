# MirrorSkills 与默认技能树整合

LIStorm `6.0.0-alpha.5+26.1.2` 包含 MirrorSkills 代码和 Serenia 已迁移的技能树。仓库仍只有 LIStorm、Journey、Sync Sign Notice 三个构建模块。

## 来源与许可

- MirrorSkills：本地可用源提交 `3057c270b9a1612f85cca34cb002d1d37f162859`，作为合并父历史保留。代码保持原包名，只有初始化入口改由 LIStorm 调用；客户端代码移入 client 源集。原 LICENSE 为 CC0 1.0，保存在 `listorm/licenses/MirrorSkills-LICENSE`，不沿用旧元数据中不一致的 MIT 标注。
- 技能树：2026-10-23 从 `Serenia/Journey/datapacks/mirrortree-skill-tree` 导入 11 份 JSON，逐字节保留，来源校验值见 [skill-tree-source.json](skill-tree-source.json)。原 Unlicense 也随 JAR 分发。不采用 Astreet 的副本，不引入原数据包的 mod 元数据或 pack.mcmeta。
- 依赖：Pufferfish Skills 0.19.2（26.1 Fabric，Modrinth 版本 `Uhs6RdHK`）和 Attributes 0.8.2+26.1，均由 Maven 获取、单独安装；构建无需相邻实例的 JAR。

## 兼容边界

保留 `mirrorskills:skill_data` 附件、`mirrorskills:trigger_skill` 网络消息、`/mirrorskills` 命令及技能/参数名。保留 I 隐身、O 冲刺、反引号无敌按键及倒计时 HUD。技能数值、触发条件和冷却算法不变。

内置数据位于 `data/mirrortree/puffish_skills`。`mirrortree:combat` 的 16 节点与 `mirrortree:support` 的 42 节点及奖励、经验公式保持原值；辅助树沿用 LIStorm 的食物、群系、进度、矿石经验源。不迁移或清空 Pufferfish 玩家进度。

LIStorm 声明提供 `mirrorskills` 兼容别名。运行时必须移走独立 MirrorSkills JAR，防止两个实现同时注册附件、网络和 Mixin。Journey 的梦境逻辑仍在 Journey 模块。

## 升级

1. 关闭客户端；通过服务器既有管理面板停服并等待保存结束。
2. 备份旧 LIStorm、独立 MirrorSkills JAR，以及世界中的 `datapacks/mirrortree-skill-tree`。
3. 两端替换为新 LIStorm，并将独立 MirrorSkills 移出 mods。其余模组维持原版本，Pufferfish Skills / Attributes 仍需保留。
4. 将同名外部技能树移至世界 datapacks 目录之外；内置资源自动加载。外部同路径资源优先级更高，留在原目录会覆盖后续内置更新。其他自定义数据包保留。
5. 重启并检查日志中的技能树加载、原玩家技能/冷却、技能按键和 HUD。升级不修改世界、玩家文件或数据库。若回滚，停服后同时恢复旧 JAR 与外部技能树。

## 验证入口

根目录 `./gradlew build :journey:validationJar` 执行编译、原有检查、SkillData codec/副本隔离回归及服务端 GameTests。技能 GameTest 验证内置 58 节点、属性/命令奖励、LIStorm 经验与药水效果复制/序列化标记。

`:listorm:runClientGameTest` 包含实际按键 → 服务端技能 → 同步 HUD 数据测试，关闭并重新打开临时世界后检查技能节点、冷却与跨维度死亡位置。`python tools/verify_distribution.py` 核验发行包只包含正式模组、技能类/Mixin/许可及源技能树字节校验。Journey validation JAR 仅编译，不安装正式服。

2026-10-23 本地验证通过：根目录 `build :journey:validationJar`（3 项服务端 GameTests、SkillData codec/副本隔离回归）、`:listorm:runClientGameTest`（含按键/同步/冷却及临时世界重开）和两端发行包校验。旧技能实现除两个初始化入口外保持一致；11 份技能树 JSON 与 Serenia 来源校验值一致。此记录不代替正式服重启与真人连接验收。

后续源码清理及新增验证见 [MirrorSkills 代码审查](SKILLS_CODE_REVIEW.md)；上面的源码一致性说明描述整合提交时的状态。
