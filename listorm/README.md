# Lantern in Storm · 26.1.2

把 Lantern in Storm、本地 Spirit in Storm 2.0.1、AnnoyingEffects ui 1.2.2 的内容合并到一个 Fabric 模组。当前版本 `6.0.0-alpha.3+26.1.2`，供新世界集成测试。与 StarryTrees 无关，不提供旧存档转换。

## 内容

- 34 种灯方块、36 个物品、2 种起点灯实体，沿用原资源 ID、模型和配方。
- 灯具稳定区、开关、边界粒子；护符消耗与补充、手电防护与充能；入梦点和重生回退。
- 35 种 AnnoyingEffects 效果及配置、梦魇累计、客户端 HUD、输入和渲染表现。
- Spirit 的食物、首次群系、目标/挑战进度、矿石经验来源，接入 Pufferfish Skills。
- Farmer’s Delight 食用事件；Biomes O’ Plenty / Mythic Metals 可选配方和材质。

## 安装与运行

客户端和服务端均安装本项目内容 JAR，以及以下依赖：

| 依赖 | 构建和测试使用版本 |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| Trinkets Updated | 4.0.1+26.1 |
| Pufferfish’s Skills | 0.19.1（26.1 Fabric 发行包） |

三个原内容模组已合入本项目，不要再同时安装旧 JAR。第三方依赖单独安装，不嵌入内容 JAR。Farmer’s Delight、Biomes O’ Plenty 和 Mythic Metals 为可选模组；缺少材料模组时，其配方不加载，相关灯使用原版材质回退。

护符和手电可佩戴于 Trinkets 的 `offhand/glove` 槽，也可持在主手/副手。佩戴物死亡保留。效果配置首次启动生成于 `config/AnnoyingEffects.json`，修改后重启生效，缺失字段补默认值。玩家次数与入梦点保存于世界内 `lantern-in-storm/players/<UUID>.json`。

## 维度放置规则

`mirrortree:bedroom` 恒定安全，无需灯具、入梦点或防护装备；不会因环境产生梦魇，也不消耗防护耐久。

所有维度均可放置本模组的各类灯，仍受服务器的建造权限约束。球球世界（`starry_skies`）的点亮与防护能力维持原规则；普通灯不会因此获得神话金属灯的防护。卧室始终是 LIStorm 安全区，无需灯具防护。

## 测试技能数据包

源码没有附带服务器技能树。本工程使用独立测试类别验证真实经验到账，并附带 [示例数据包](examples/spirit-experience-datapack/README.md)。示例只有经验类别，没有技能和奖励；它不随内容 JAR 自动加载。

## 开发

使用 JDK 25 和附带的 Gradle 9.7.1 wrapper：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$PWD/.gradle-home"
./gradlew genSources build --no-daemon
./gradlew runClientGameTest runGameTest --no-daemon
python3 tools/audit_resources.py
```

产物位于 `build/libs/`。`genSources` 生成官方名称的游戏源码；本机解包副本位于 `build/minecraft-sources/`。游戏源码和缓存不打入发行包。

可选兼容测试：

```sh
./gradlew runClientGameTest -PfarmersDelightTestJar=/absolute/path/FarmersDelight.jar
./gradlew runClientGameTest -PcompatTestDir=/absolute/path/compatible-mod-jars
```

兼容目录须包含材料模组及其自身依赖。测试在临时新世界执行，不使用既有玩家存档。

## 实现与验证

- [模块结构和行为调整](docs/IMPLEMENTATION.md)
- [测试范围、结果和验收项](docs/TESTING.md)
- [原始迁移调查与计划](docs/MIGRATION_PLAN.md)

源代码采用 MIT，保留三个项目的作者和许可证，见 `licenses/`。第三方代码不包含在本项目源码或内容 JAR 中。
