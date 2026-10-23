# 代码清理与静态性能分析

审查日期：2026-10-23。基线：`dfc47887c55e045d49dafe8a779b4a343d448358`。范围：LIStorm、MirrorTree Journey、Sync Sign Notice 的 Java 源码、相关入口与构建边界。

本轮完成源代码清理及保持现有行为的局部优化。性能判断来自调用关系、循环次数和分配位置，没有进行性能基准测试，不能据此宣称 FPS、TPS 或实际延迟提升。未部署至正式客户端或服务器。

## 清理结果

- Java 文件由 103 个减为 78 个，含测试的 Java 行数由 5448 行减为 4899 行；新增测试也计入后者。
- LIStorm 删除 10 个没有消费者的效果标记接口，以及 14 个仅重复基类默认行为的效果子类。每个效果 ID 仍注册独立实例，保留 35 个效果的注册顺序、属性修饰和配置默认值。
- 删除效果类中的空构造函数、默认实现转发、未使用 import、模板说明及失效实验注释。`WaterFillingStatusEffect` 仅移除注释、多余作用域和末尾未使用的读取，实际循环与世界修改顺序保留。
- `AnnoyingEffects` 改为直接静态初始化，去掉不作为 Fabric 入口使用的 `ModInitializer` 包装；`NoSavePlayerData` 去掉未调用的 UUID 重载转发和清理方法，收窄内部方法可见性。
- Journey 删除空客户端入口 `MTClient`；把实际客户端 mixin 原样迁至 `src/client/java`，通过 Loom 分离客户端与通用源码。移除配置类无调用者的方法，默认配置写入直接完成；日志使用本模块 logger。
- `TopTrapdoorDecorator` 去掉只调用一次的泛型注册包装。保留触发静态注册的 `init()`，该方法虽为空，调用仍有实际作用。
- Sync Sign Notice 合并重复的空结果与缓存发布路径，移除单次转发方法。保留异步刷新入口、完整不可变快照和串行刷新边界。
- 显式 import 的静态使用筛查未发现剩余未引用项。删除 import 只改善源码维护，不产生运行时加速。

## 已实施的静态优化

| 位置 | 改动 | 可支持的结论与边界 |
| --- | --- | --- |
| `DefenseEquipment.find` | 从遍历全部饰品改为直接查询 `offhand/glove` | 查找扫描由全部饰品槽 T 缩至手套槽 G；仍按普通槽、cosmetic 槽顺序取最后匹配项，然后回退主手、副手，替换写回原目标 |
| `SpiritLanternBlock.getShape` | 复用两个固定形状 | 避免每次形状查询重复构造；碰撞尺寸不变 |
| `StormClient.hud` | 复用固定纹理 Identifier | 减少每帧字符串拼接和 Identifier 创建；显示阶段规则不变 |
| `StormNetworking.bar` | 状态归一化后，相同状态直接返回 | 避免重复创建状态记录和 Map 写入；负 stage 的继承、范围限制和首次发送规则保留 |
| `LanternIndex.isSafe`、`ProgressionService.tick` | 外提维度判断、复用 bedroom Identifier | 减少循环内重复判断和每秒对象创建；仍只查询邻近已加载区块 |
| `MTDream.tick` | 无待搜索玩家时提前返回 | 避免空队列每 tick 创建快照列表；卧室冒险模式约束仍先执行 |
| `MTDream.nearbySafe` | 跳过已检查的内圈，不再为每个位置创建正负高度数组 | 当前固定边界下，最坏安全查询由 1155 次减为 567 次；保留候选位置首次出现顺序。仍遍历方形后跳过内圈，算术循环仍是 O(H·R³)，昂贵世界查询为 O(H·R²) |
| `TopTrapdoorDecorator.generate` | 平方距离替代开方，外提中心、范围及不变 block state | 仍为 O(R³)；减少每个候选的计算。保留 XYZ 顺序与三个独立分支，交叉位置的重复放置和随机消耗顺序保持一致 |
| `NewsService` | 删除先行 `COUNT(*)`，直接读取排序后的有限结果并发布 | 每次刷新由两次 SQL 往返减为一次；空结果仍清空显示与磁盘缓存。未检查数据库索引和执行计划，不能声称查询复杂度变为 O(k) |

## 保留的结构与后续候选

以下项目经审查保留，避免把行为调整混入清理：

- `AnnoyingStatusEffect` 是梦魇计数的类型边界；`TanglingDreamsStatusEffect` 区分弱化参数；`StatusEffectInstanceStackHolder` 支撑 mixin 延迟效果队列。它们都有调用用途。
- 对外 LIStorm API、网络 payload、配置与存档 DTO、异步任务边界仍各自独立。未迁移注册 ID、资源路径、包协议或玩家存档格式，也未引入跨模块通用配置框架。
- 已移除类型在本仓库、已检查的 47 个外部客户端 JAR 和 MirrorSkills 源码中未发现引用；这不构成对未知第三方通过反射依赖内部类名的兼容保证。

| 候选 | 静态风险 | 本轮处理 |
| --- | --- | --- |
| `EndermanHostileStatusEffect` | 每秒对每个受影响实体查询边长 2048 的实体区域；实际成本取决于已加载实体分区和候选数 | 未缩小范围，缩小会改变效果规则 |
| `WaterFillingStatusEffect` | 每个活动 tick 扫描 7³=343 个位置，涉及距离、方块读取、液体与邻居更新 | 保留运算与修改顺序；液体更新可能比算术更昂贵，待隔离实测 |
| `TargetedStatusEffect`、`HorselessStatusEffect` | 逐 tick 实体查询及重复距离/方向计算 | 未调整近零向量处理、爆炸或效果频率，避免行为漂移 |
| `TanglingNightmare` | 每 tick 随机与集合操作，效果序列依赖随机调用次序 | 未合并或重排随机调用 |
| `SafetyService` | 全域安全维度仍查询附近灯 | 附近灯结果还用于装备充能，不能只按“安全”结果直接跳过 |
| `BorderParticles` | 每 30 tick 发送与边界采样数相关的粒子 | 未减少数量或频率，属于可见效果取舍 |
| `MTDream` 首次入梦 | 尝试次数按玩家限制，并非全服每 tick 预算；多名玩家可同 tick 各同步加载一个候选区块 | 全局预算或异步生成需单独设计和测试时序 |
| `DreamStore.save`、玩家状态持久化 | 服务器线程上的文件写入可能产生延迟 | 保留离开主世界前先保存的顺序；异步化需同时解决完成、失败和退出语义 |

## 验证

- 修改前基线：LIStorm 的 2 个服务端 GameTest、Notice 的 9 项客户端断言通过。
- `python journey/tools/check_loop_equivalence.py`：通过。验证当前搜索范围内候选首次出现顺序；半径 2..128 的整数坐标圆环判断一致；半径 2..24 的 96024 次放置／随机事件顺序一致。这是有限范围算法等价检查，不运行 Minecraft 世界，也不是性能测试。
- `gradlew.bat clean build :journey:validationJar :listorm:runClientGameTest :sync-sign-notice:runClientGameTest --no-parallel --console=plain`：通过，进程退出 0。
- LIStorm：2 个服务端 GameTest 通过，覆盖新增的短触距与沉重效果属性断言；客户端 GameTest 通过，包含新增 15 项装备选择／替换断言，以及现有 HUD、灯具、卧室安全、梦境效果与存档重载检查。
- Notice：12 项客户端断言通过，包含单次 LIMIT 查询、结果发布、空结果清空及磁盘缓存读取。JDBC 查询使用替身连接，实际标牌 mixin 在 Minecraft 客户端运行；没有验证正式数据库连通性。
- Journey：正式 JAR 与隔离验证探针编译通过；客户端 mixin 的移动被 Git 识别为内容完全一致的重命名。循环等价验证范围见上，未运行会修改世界的完整 Journey 探针。
- `python tools/verify_distribution.py`：客户端 ZIP 的 3 个正式模组、服务端 ZIP 的 2 个正式模组、入口、mixin、许可证、依赖均通过。额外检查确认正式 JAR 中无本次已删除类，Journey 客户端 mixin 仍存在，空入口已移除。
- `git diff --check`：通过。独立代码复核确认 Trinkets 普通／cosmetic 槽次序、网络归一化与固定形状／纹理值保持一致。
- 隔离客户端日志仍出现基线已有的测试账号认证、默认示例数据库连接及各向异性过滤提示；测试断言均完成。这些测试与构建耗时不用于推断性能收益。
