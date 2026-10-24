# MirrorTree Journey · 26.1.2

客户端物品/装饰器与梦境逻辑合为一个 Fabric 模组，mod ID 为 `mirrortree`，两端安装同一个 JAR。需要 LIStorm 和 Starry Skies，完整依赖见[安装说明](../docs/INSTALLATION.md)。

- 客户端窗口标题显示 `MirrorTree · AnotherAutumn | 26.1.2-Fabric | Ciallo～(∠・ω< )⌒★`；周目标识随客户端模组版本更新，Minecraft 版本自动读取，进入或退出世界后标题保持一致。
- 新玩家从服务器配置的卧室出生。普通玩家在卧室使用冒险模式；管理员用创造模式建造。
- 右键卧室的床入梦；首次在主世界半径 3000 内寻找安全落点，登记 LIStorm 入梦点和个人重生点。
- 在主世界成功躺床后，点击聊天栏「回到卧室」执行 `/wakeup`；再次使用卧室的床返回保存的位置和状态。
- `/wakeup` 仅允许正在主世界睡觉的玩家使用；`/redream` 需要管理员权限。全局出生点不随入梦改变。

快照保存在当前世界的 `mirrortree/dreams/<UUID>.json`，包含位置、生命、饥饿、饱和度、消耗度和完整效果。保留旧 `config/mirrortree/dream_data.json` 坐标记录读取，且不删除旧文件。更新不重置现有玩家、技能或物品，也不导入卧室建筑。

开发和隔离验证规则见 [AGENTS.md](AGENTS.md)，许可见 [LICENSE](LICENSE)。
