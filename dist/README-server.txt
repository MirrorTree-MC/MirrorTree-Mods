MirrorTree Mods — Minecraft 26.1.2 服务端包

停服后，先备份并移走同 mod ID 的旧 JAR，再将 mods/ 中的两个
JAR 放入服务端 mods 文件夹，随后启动服务端。
本包包含：
- lantern-in-storm-rebirth 6.0.0-alpha.5+26.1.2
- mirrortree 6.1.0+26.1.2

运行环境与单独安装的依赖：
- Java 25，Minecraft 26.1.2，Fabric Loader 0.19.5+
- Fabric API 0.155.3+26.1.2
- Trinkets Updated 4.0.1+26.1
- Pufferfish's Skills 0.19.2（26.1 Fabric）
- Pufferfish's Attributes 0.8.2（26.1 Fabric）
- Starry Skies 4.2.0+26.1

LIStorm 已包含原 AnnoyingEffects、Spirit in Storm 与 MirrorSkills，
不要同时安装这三个旧模组。Sync Sign Notice 仅安装在客户端。
现有配置、世界与玩家数据继续保留。LIStorm 内置 Serenia 默认技能树。
停服时将原 mirrortree-skill-tree 数据包备份移出世界 datapacks 目录，
避免外部副本覆盖内置版本；不要删除 Pufferfish 玩家数据。
本包不会导入卧室建筑或重置玩家；沿用实例中已有的卧室配置。
不要向正式服安装 bedroom-regression、GameTest 或 sources JAR。
ZIP 校验值见同批发布的 SHA256SUMS。

源码与各模块许可证：
https://github.com/MirrorTree-MC/MirrorTree-Mods
