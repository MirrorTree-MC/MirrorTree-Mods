# MirrorTree Journey — 26.1.2

客户端物品/装饰器与旧 server 分支的梦境逻辑合并为一个 Fabric 模组，两端安装同一个 JAR。维护入口见 [AGENTS.md](AGENTS.md)。

- 新玩家仍从服务器配置的卧室出生。普通玩家在卧室使用冒险模式；管理员用创造模式建造。
- 卧室右键床入梦。首次在主世界半径 3000 内寻找安全落点，登记 LIStorm 入梦点及个人重生点。
- 在主世界成功躺床后，点击聊天栏「回到卧室」执行 /wakeup。再次使用卧室的床返回保存的位置和状态。
- /wakeup 仅允许正在主世界睡觉的玩家使用；/redream 需要游戏管理员权限。
- 全局出生点不随入梦改变。不会重置现有玩家、技能或物品。

梦境快照保存在当前世界的 mirrortree/dreams/<UUID>.json，包含位置、生命、饥饿、饱和度、消耗度和效果。旧 config/mirrortree/dream_data.json 的坐标记录可读，迁移不会删除旧文件。

编译：将 LIStorm 源码置于相邻 ../LIStorm，先构建 alpha.4 的 JAR，再用 Java 25 执行 ./gradlew build。StarrySkies 4.2.0+26.1 由 Modrinth Maven 获取。
