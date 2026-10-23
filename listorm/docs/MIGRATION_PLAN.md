# Lantern in Storm 系列合并与 Minecraft 26.1.2 迁移计划

## 范围、基线与已完成准备

目标是把 Lantern in Storm、Spirit in Storm、AnnoyingEffects 的已实现功能整理为**一个 Fabric 内容模组**，目标游戏版本为 Minecraft Java 26.1.2。StarryTrees 与此任务完全无关。用户会开新档，因此**不做 1.21.1 旧存档或旧配置升级**；新世界中的玩家进度、重生点和配置仍须正常持久化。

本地卷 `/Volumes/熊脑/小程序们/` 的三个项目已复制到工作区 `../local-reference/`，包括未提交的改动和未跟踪资源；原件没有被修改。GitHub 克隆位于 `../Lantern-in-Storm`、`../Spirit-in-Storm`、`../AnnoyingEffects`，用于历史比对。优先采用本地快照的实际代码与资源，README 的旧描述和 GitHub 的旧版本只作为线索。

| 迁移基线 | 本地参考 | 已确认内容 |
| --- | --- | --- |
| Lantern in Storm：提交 `3dff58e` 加本地未提交内容，1.21.1 / 5.0.0 | `../local-reference/Lantern-in-Storm` | 16 个 Java 文件、315 个资源文件；34 个灯方块、36 个实际注册物品（白纸灯物品常量被注释）、2 种起点灯实体；安全区域、护符/手电、重生点、边界粒子。未提交修改调整了安全区清除效果、手电充能和佩戴逻辑，补了多种金属灯的模型与配方。 |
| Spirit in Storm：提交 `42b7175` 加本地未提交内容，1.21.1 / 2.0.1 | `../local-reference/Spirit-in-Storm` | 8 个 Java 文件。**核心玩法已从旧版“给 Lantern 加灵魂”改成给 Pufferfish Skills 经验**：食物、群系、进度、矿石事件；新增按次数递减的经验规则与 `SiSExperienceSource`。旧的铁砧胡萝卜彩蛋已删除。 |
| AnnoyingEffects：`ui` 分支提交 `324b72e`，1.21.1 / 1.2.2 | `../local-reference/AnnoyingEffects` | 69 个 Java 文件、35 种注册效果、14 个 Mixin；包含进度球 HUD、服务端网络包、效果配置及客户端控制/渲染。与 GitHub `ui` 分支源码一致。 |

Lantern 附带的 `annoyingeffects-1.2.0.jar` 和 Spirit 附带的 `lantern-in-storm-0.1.3.jar` 都已保留供核对。Spirit 的本地 2.0.1 源码已移除对 Lantern 的直接依赖，因此不能按 GitHub 1.0.2 的灵魂奖励方案移植。AnnoyingEffects 的 GitHub 默认分支只有 1.1.0；应使用本地 `ui` 1.2.2。

独立目标工程 `../Storm-Rebirth-26.1.2` 已建立：JDK 25、Gradle 9.7.1、Fabric Loom 1.18.4、Loader 0.19.5、Fabric API 0.155.3+26.1.2。2026-10-22 已执行 `./gradlew genSources build --no-daemon`，构建成功。Loom 反编译了 **6,882 个** Minecraft Java 类，源码 JAR 为 `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.1.2/minecraft-merged-043a8b3edf-26.1.2-sources.jar`；已抽查 `ServerPlayer.java`、`MobEffect.java`、`LanternBlock.java`。准备阶段产物为空工程。后续迁移实现与验证结果见 `IMPLEMENTATION.md` 和 `TESTING.md`。

## 技术路线

26.1 起 Minecraft 不再混淆，原代码的 Yarn 名称和旧 `fabric-loom`、`mappings`、`modImplementation`、Java 21 配置不能直接沿用。迁移时按功能追踪已反编译的 26.1.2 官方名称和调用路径，再更换对应 Fabric API。HUD 改用 `HudElementRegistry`；物品交互、物品栈、状态效果、玩家重生、客户端渲染和数据格式需要逐项验证。

新代码按职责分为 `lantern`（内容注册与模型）、`safety`（稳定区和灯具状态）、`progression`（Spirit 事件与经验）、`effects`（AnnoyingEffects 规则）、`client`（HUD、粒子、输入与渲染）、`compat`（第三方模组）。一个模组入口协调注册；服务器计算玩法并保存进度，客户端只负责显示和输入表现。以事件/API 优先，保留确有必要且有测试覆盖的 Mixin。

沿用现有内容 ID 与资源命名空间以保持配方、模型、数据包与用户配置直观可识别，但不投入旧世界 ID 转换工作。目标主 mod ID 暂定 `lantern-in-storm`。三个源码模块应直接编译成一个内容 JAR，不嵌套三个旧 JAR。Pufferfish Skills、Trinkets Updated、Farmer's Delight Refabricated、Mythic Metals 等第三方内容按其功能决定必需或可选关系；Fabric API 仍是平台依赖。Pufferfish Skills 和 Trinkets Updated 均有 26.1.x 版本，实际 API 与依赖坐标须在移植时锁定并运行验证。旧 Trinkets 饰品槽无法直接复用，新的护符/手电佩戴方式要保留同等操作体验。

## 实施顺序与验收门槛

1. **冻结玩法矩阵。** 以本地快照列出每个灯、物品、实体、配方、模型、效果、经验事件和客户端表现；逐项标记已实现、半成品、仅 README 设想。核对本地未跟踪资源是否完整，尤其金属灯的方块状态、模型、配方与可选材料。记录关键数值和效果概率作为后续回归基线。
2. **完成单模组内容注册。** 迁移所有已实现灯具、实体、物品、语言、模型、配方、战利品和数据生成；先保证 26.1.2 客户端与专用服务器可启动，新增世界中每个内容都可获取与使用。数据包中引用第三方物品的配方做条件加载，缺失第三方模组时不报错。
3. **重写 Lantern 核心循环。** 保持本地版的安全区、灯具开关、手电充能/消耗、护符抵御、负面效果清理、重生点和粒子表现。将每 20 tick 的周边区块遍历改成随灯具和区块变化维护的空间查询，保证未加载区块不触发生成。测试半径边缘、跨区块、多人、死亡/重生、不同维度与装备槽。
4. **迁移 AnnoyingEffects。** 先完成效果注册、可配置参数和服务端调度，再按 35 种效果逐个移植；客户端 HUD、控制限制、聊天限制及渲染另行隔离。随机效果使用可重复的测试随机源校对分布；每种效果测试开始、叠加、结束、死亡与断线恢复。README 明确未实装的效果不作为“功能一致”验收对象。
5. **迁移 Spirit 经验来源。** 以本地 2.0.1 代码为准：食物经验 `10/次数 + 1`、首次群系 +30、目标进度 +10、挑战进度 +50、同类矿石经验从 10 递减到 1。将事件接入新版 Pufferfish Skills API，保证次数持久化且不能因重复事件刷经验。Farmer's Delight 派单独做可选兼容。彩蛋类型目前在本地代码没有经验奖励，应按实际代码标为无奖励，除非后续确定设计修改。
6. **集成回归。** 在**全新 26.1.2 世界**跑客户端和专用服务器，验证单人与多人、35 种效果、全部灯具、Spirit 四类经验事件、HUD/网络同步、重启后进度、配置、各第三方模组有/无组合。记录与本地 1.21.1 行为的差异，明确哪些是兼容修复，哪些需要再调整。
7. **发行交付。** 一个内容 JAR、可复现构建说明、玩法与依赖说明、测试记录；保留三仓库 MIT 许可证与作者声明。AnnoyingEffects 旧 `fabric.mod.json` 标注 CC0，而 `LICENSE` 是 MIT；发行元数据应与实际许可证文本核对一致。

## 当前风险

- **本地代码未提交。** Lantern 和 Spirit 的未提交代码/资源是实际迁移基线，已复制快照防止原目录后续变化；实施时不能用 GitHub 克隆覆盖它们。
- **第三方技能树配置缺失。** Spirit 源码只注册 `spirit-in-storm:experience` 来源，没有包含 Pufferfish Skills 的技能树/类别数据包。需要在服务器企划文件中找到实际配置，才能端到端验收经验进入目标技能树。
- **客户端改动集中。** AnnoyingEffects 的 14 个 Mixin 包含输入、聊天、实体和世界渲染，26.1.2 HUD 与游戏渲染接口变化较大。必须按效果逐项实机验证。
- **旧逻辑性能与边界。** Lantern 高频扫描、AnnoyingEffects 全实体 tick 和效果队列有优化空间；性能修改须保持半径、权重及触发条件。
- **本地资源可能是草稿。** 金属灯模型、配方和生成脚本未提交，有的资源位置看起来不完整；先做资源引用检查，再确定可发行内容。

## 官方及上游资料

- Fabric 26.1 发布说明：https://fabricmc.net/2026/03/14/261.html
- Fabric 26.1.2 迁移文档：https://docs.fabricmc.net/26.1.2/develop/porting/
- Fabric 26.1.2 API 迁移表：https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api
- Fabric 26.1.2 官方模板：https://github.com/FabricMC/fabric-example-mod/tree/26.1.2
- Minecraft 26.1.2 发行说明：https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1-2
- Trinkets Updated：https://modrinth.com/mod/trinkets-updated
- Pufferfish Skills：https://modrinth.com/mod/skills/versions
- Farmer's Delight Refabricated：https://modrinth.com/mod/farmers-delight-refabricated/versions
