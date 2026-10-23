# Spirit 经验测试数据包

把整个 `spirit-experience-datapack` 文件夹复制到新世界的 `datapacks`，然后 `/reload` 或重启世界。需要本模组及 Pufferfish’s Skills。

类别 ID：`storm_example:integration`，显示名 `Storm Integration Test`。每级 1000 经验，最高 10 级，无技能、连接、奖励或死亡清零。食物、群系、进度和矿石四种来源共同汇入这个测试类别。

可用 Pufferfish’s Skills 的类别界面/命令查看经验。食用新食物得 11 经验，第二次得 6，逐渐降到 1；首次进入每个群系得 30；原版目标/挑战进度分别得 10/50；同类矿石从 10 逐渐降到 1。普通任务进度和本地未实装的彩蛋不奖励经验。

`experience.json` 中来源格式为：

```json
{"type":"spirit-in-storm:experience","data":{"type":"food"}}
```

`data.type` 支持 `food`、`biome`、`advancement`、`ore`。可以把这些来源放进正式服务器技能类别。本示例只是验证载体，不替代服务器的技能树设计。
