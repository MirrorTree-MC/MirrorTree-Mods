# alpha.4 bedroom/lamp regression (2026-10-23)

The combined MirrorTree dedicated-server probe passed 94 assertions, including bedroom login cleanup, no defense wear, all 33 lamp items placed in each dimension, unchanged Starry Skies protection, and exhausted-doll debuff retention. The two existing headless LIStorm GameTests also passed. The older client suite below was updated for unrestricted lamp placement but was not rerun in full for this change.

# 验证记录 · 更新于 2026-10-23

当前交付是 `6.0.0-alpha.3+26.1.2`。这是已实现并通过自动化集成测试的迁移版，尚未做长期玩家试玩和真实网络压力测试；不把“能编译”视为全功能验收。

## alpha.3 卧室安全规则回归（2026-10-23）

执行 `runClientGameTest runGameTest build --no-daemon`，全部通过。日志：`build/alpha3-regression.log`。

- 清空 API 安全维度注册，在无灯具、无入梦点、宽限为零、未装备防护的情况下进入 `mirrortree:bedroom`，确认安全。
- 进入时清除已有强/弱梦魇，累计条归零。
- 分别佩戴玩偶与护符，连续执行 5 次每秒防护更新，确认不产生环境梦魇、不消耗装备耐久。
- 离开卧室进入无防护的 `starryskies` 测试维度，确认恢复普通梦魇规则。
- 两个维度的新放置灯具限制继续由 alpha.2 的逐灯实际放置测试覆盖。

## alpha.2 修复回归（2026-10-23）

执行 `runClientGameTest runGameTest build --no-daemon`，成功。日志：`build/alpha2-regression.log`。本次修改不依赖可选模组，使用基础依赖执行。

- 耗尽玩偶佩戴于自定义维度，连续执行 5 次每秒防护更新：缓慢效果保留，梦境缠绕保持同一效果实例，不被清除重加。
- 恢复电量后重新清理有害效果；原版维度中耗尽的玩偶仍按原规则免费清理。
- 在测试版 `mirrortree:bedroom`、`starryskies:placement_test` 与不受限自定义维度，逐一检查 34 种灯的放置策略；对全部 33 种有物品的灯实际调用 BlockItem 放置，验证允许/拒绝、世界方块和物品消耗。白纸灯没有注册物品，直接检查方块放置策略。
- 两个受限维度仅四种神话金属灯获准；不受限维度仍可放其他灯；主世界及其他 MirrorTree 维度策略保持不变。
- 直接加载的已有纪念灯经过邻居更新后保留。
- 原有客户端经验、效果、持久化、输入及无界面双玩家测试继续通过。测试维度文件只位于 gametest 源集，不随内容 JAR 发行，也不依赖 StarryTrees 项目。

## 环境与执行

JDK 25、Gradle 9.7.1、Loom 1.18.4、Loader 0.19.5、Fabric API 0.155.3+26.1.2、Trinkets Updated 4.0.1+26.1、Pufferfish’s Skills 0.19.1。

| 执行 | 结果 | 本机证据 |
| --- | --- | --- |
| `genSources` | 成功，6,882 个游戏 Java 类 | 游戏源码 JAR 和 `build/minecraft-sources/` |
| `build` | 成功，内容和源码 JAR | `build/libs/` |
| `python3 tools/audit_resources.py` | 34 灯、36 物品、170 个引用模型，0 错误 | 工具标准输出 |
| `runClientGameTest`，基础依赖 | 成功 | `build/gametest-final.log` |
| 加 Farmer’s Delight | 成功，吃派只奖励一次 | `build/gametest-farmersdelight.log` |
| 加全部可选模组及依赖 | 成功，条件配方和材料资源包加载 | `build/gametest-materials.log` |
| `runServer --args=nogui` | 公共代码在专用服务器环境初始化成功，停于普通服务器 EULA 检查 | `build/server-bootstrap.log` |
| `runGameTest` | 无界面测试服务器进入并关闭世界；本项目双玩家场景通过 | `build/gametest-server.log` |

无界面运行使用 Fabric 官方 GameTest 框架，不修改普通服务器的 EULA 设置。框架报告共 2 个必需测试通过，其中本项目提供 1 个双玩家测试，其余为平台测试。

所有世界均为测试新建；参考源码、本地原项目和既有玩家存档未参与运行。日志、缓存和测试世界忽略版本控制。

## 客户端集成场景的断言

测试入口：`StormGameTest`，在真实客户端与集成服务器运行，最后关闭并重新打开同一新世界。

- 全部灯注册；点亮/关闭；立方体边界 8 包含、8.01 不包含；区块索引卸载和重建。
- 护符、核心、手电和原版木灯配方存在；BOP/Mythic 配方仅对应模组在场时存在。
- 食物、矿石衰减公式，首次群系和挑战经验；食物重复两次与群系去重真实汇入 Pufferfish 类别，净增 47 经验。
- 实际吃苹果回调；实际破坏煤矿石事件；实际目标和挑战进度事件；重复奖励去重。
- 安装 Farmer’s Delight 时，实际调用新版派食用逻辑，派片计数恰为 1。
- 饰品槽存在，手电可装备并优先识别；稳定区充能；原版维度免费清除有害效果；无防护触发弱梦魇。
- 测试自定义维度中无防护触发强梦魇、手电每秒耗电、电量耗尽保留物品并触发弱梦魇。
- 入梦点保存；没有床时重生回退到入梦点。
- 对真实生物调用 35 个注册效果的服务端回调，验证 API 和运行时链接；累计条满时产生可应用的效果队列。
- 效果同步到客户端；横移、镜像、自动跳跃与寒冷抑制跳跃的输入断言；HUD、物品、起点实体渲染截图。
- 关闭/重开后，食物次数、群系去重记录和入梦点仍存在。

测试类别和测试维度仅在 `src/gametest`，不进入内容 JAR。用户要求的可运行经验示例另存于 `examples/spirit-experience-datapack/`，并打包为 `build/libs/storm-experience-test-datapack.zip`。

## 无界面服务端双玩家场景

测试入口：`StormServerGameTest`。两个独立 UUID 的服务端玩家使用内存网络连接登录：

- 同一时刻一个在灯边、一个在稳定区外，各自安全状态和梦魇效果正确。
- 同一种食物的次数分别为 1 和 2，不互相覆盖。
- 重力效果在服务端产生额外下沉速度。
- 两份玩家记录保存，退出清理，测试服务器正常关闭。

这验证了服务端环境和玩家状态隔离，不等于两台真实客户端的网络延迟/掉线压力测试。

## 可选模组组合

已运行组合：Farmer’s Delight `26.1-3.6.21+refabricated`、Biomes O’ Plenty `26.1.2.0.22`、Mythic Metals `0.26.0-alpha.5+26.1.2`，以及 Alloy Forgery 3.1.1、owo 0.13.1+26.1、GlitchCore 26.1.2.0.2、TerraBlender 26.1.2.0.3。下载文件 SHA-512 记录于 `COMPAT_FIXTURES.json`，依赖 JAR 不随发行物打包。

日志确认 `lantern-in-storm:mythic_materials` 与 `lantern-in-storm:bop_materials` 已启用；没有本项目的缺失贴图、无效标签、配方解析错误。

组合日志出现 `RecipeInjector` 针对 `mythicmetals:adamantite_forge_recipe` 的重复 ID 错误；本项目未定义该 ID，测试仍通过。没有修改第三方模组。测试环境另有 macOS OpenGL 着色器警告、离线测试账号的 Realms 授权日志、各模组标签翻译提示；无界面首次运行会生成缺失的 `server.properties`。这些日志不作为本项目功能成功的证据，也没有通过静默过滤来隐藏。

## 验收边界

35 个效果的回调覆盖不代表每个概率分支、所有客户端视觉交互都已穷举。尚需玩家试玩确认镜头偏移、聊天复读/失语、自动挖掘/使用、爬行、长时间水域与多种效果组合的手感；长时间性能、真实网络多人及第三方不同版本组合未压测。正式服务器的技能与奖励仍需企划数据包，本交付仅提供用户要求的测试类别。

因此版本明确标记为 alpha；可开始新世界测试，不宣称完成正式发行验收。行为修复与优化差异见 `IMPLEMENTATION.md`。
