# Lantern in Storm · 26.1.2

LIStorm 合并 Lantern in Storm、Spirit in Storm、AnnoyingEffects 和 MirrorSkills，mod ID 为 `lantern-in-storm`。客户端与服务端安装同一内容 JAR；依赖、升级和存档边界见[安装说明](../docs/INSTALLATION.md)。不要同时安装这些内容的独立旧模组。

## 内容与使用

- 34 种灯方块、36 个物品、2 种起点灯实体，保留原资源 ID、模型和配方；包括稳定区、开关、边界粒子和入梦点/重生回退。
- 护符和手电可佩戴于 Trinkets 的 `offhand/glove` 槽，或持在主手/副手；佩戴物死亡保留。灯旁可充能，电量耗尽保留手电供再次充电。
- 35 种梦魇效果、累计 HUD 与输入/渲染表现。`config/AnnoyingEffects.json` 首次启动生成，缺项补默认值，修改后重启生效。
- 食物、首次群系、目标/挑战进度、矿石经验接入 Pufferfish Skills；次数与入梦点保存在世界内 `lantern-in-storm/players/<UUID>.json`。
- MirrorSkills 主动技能、被动能力和冷却 HUD；解锁「免费药箭」后默认按 J 领取 16 支随机药水箭，冷却 5 分钟，按键可在控制设置中修改；内置 Serenia 的战斗树 16 节点、辅助树 42 节点。升级时须处理独立 MirrorSkills JAR 和旧技能树数据包，见[安装说明](../docs/INSTALLATION.md)。

`mirrortree:bedroom` 恒定安全，无需灯具、入梦点或防护装备，不产生环境梦魇、不消耗防护耐久。各维度均可按服务器建造权限放置灯具；Starry Skies 的点亮/防护仍遵循原规则，普通灯不会因此获得神话金属灯的防护。

Farmer’s Delight 食用事件、Biomes O’ Plenty / Mythic Metals 配方和材质为可选兼容。缺少材料模组时，不加载相关配方并使用原版材质回退。

附带的[经验示例数据包](examples/spirit-experience-datapack/README.md)只有经验类别，没有技能奖励，不随内容 JAR 自动加载。LIStorm 不提供跨 Minecraft 版本的旧存档转换；仍需玩家确认长期体验及复杂效果组合。

开发维护见 [AGENTS.md](AGENTS.md)。原内容采用 MIT，MirrorSkills 为 CC0 1.0，默认技能树为 Unlicense；[许可原文](licenses/)及[来源说明](../docs/UPSTREAM.md)保留。
