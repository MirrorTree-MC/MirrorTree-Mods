# ModpackUpdater · 26.1.2

面向 Minecraft 26.1.2 / Fabric / Java 25 的客户端更新器，保留 `modpackupdater` ID 和 `config/modpackupdater.json`。更新文件直接来自管理员自己的服务器固定 HTTP(S) 端口。服务器不需要安装此模组；[独立更新服务](tools/update_host.py)可以和 Minecraft 同机运行，更新发布不需要重启 MC。

每次启动都在后台检查；发现更新后，在主菜单自动打开更新确认页，可预览模组名称、版本和下载大小。点击「更新并重启」确认后，会自动下载、暂存并校验全部变化文件，再退出游戏、备份并应用更新，最后自动启动新版游戏。也可选择「稍后更新」继续使用当前版本。主菜单右上角「整合包更新」支持手动检查。网络或文件校验失败时保留当前整合包，不会退出或启动新版游戏。

关闭前在游戏内显示 3 秒倒计时，说明将发生的操作；关闭后由独立窗口显示等待退出、核对文件、备份、应用和重新启动进度。新版游戏启动后，会再显示更新完成页和发布版本。独立窗口使用 JDK 桌面组件，支持 Windows、macOS、Linux 图形桌面；无可用桌面时保留相同的应用日志，游戏内提示仍有效。Linux 桌面进度窗口需要 Java 能访问 X11/XWayland。应用失败时显示错误并保留日志，不会循环启动游戏。

配置示例：

```json
{
  "serverUrl": "http://your-server.example:18881/",
  "trustedPublicKey": "由管理员提供的 Ed25519 DER 公钥，使用 Base64 编码",
  "checkOnStartup": true
}
```

主菜单允许手动检查和取消暂存更新；上次已暂存的更新可选择「应用并重启」。后台检查不会自动下载，只有确认后才执行更新。旧 `checkOnStartup` 字段为配置兼容保留，不再关闭启动检查。旧 `updateUrl` 仅能迁移到具备签名和新协议的服务；旧版未签名云清单不再自动执行。原版本记录文件保留，比较依据改为实际文件 SHA-256，支持相同版本号下的修复以及更新器自身更新。

自动重启复用当前 Java、游戏参数和运行目录，并保留启动器可能清理的原生库。登录参数只临时保存在实例的 `.modpackupdater/restart.properties`，仅当前系统用户可读取，启动成功或应用失败后删除；不会上传服务器。重启后的输出在 `.modpackupdater/restarted-game.log`。若启动环境已经被外部删除，可从启动器重新打开游戏；已应用的文件和备份仍在。

更新按 Fabric mod ID 定位旧版，即使 JAR 文件名变化也能替换。发布清单只规定必须安装且保持最新的内容；用户额外添加的模组、配置、资源包和本地世界数据包保留。从清单移除只停止分发，不远程删除客户端文件。3.0.1 起忽略旧协议的 `removeModIds`，发布工具也拒绝旧 `--remove-mod` 参数。默认只发布 `mods`；需要分发公开配置或资源包时逐项使用 `--extra`。更新器的服务器地址和信任公钥配置受保护。

替换前的文件保存在实例的 `mod-update-backups/updater-*`；下载和事务日志在 `.modpackupdater/`。若上一次应用中断，独立应用器先恢复备份，再处理待应用计划。应用日志在 `.modpackupdater/apply.log`。无法启动游戏时也可以用 Java 25 手动运行已提取的应用器：

```sh
java -jar /path/to/instance/.modpackupdater/apply-helper.jar /path/to/instance 0
```

执行这条恢复命令前关闭该实例；最后的 `0` 表示不等待其他进程。路径/备份损坏或本地文件在下载后变化时，应用器会停止并保留恢复信息。

## 自托管更新服务

### 问答发布向导

已有更新服务时，在服务目录执行 `sh publish-update.sh`；也可运行 `python3 tools/publish_update.py`。仓库内的脚本可用 `--base /srv/update-service --key /srv/private/release.pem` 指定目录和已有密钥。需要 Linux/macOS、Python 3.10+，并将 `publish-update.sh` 和 `tools/` 中的两个发布脚本放在同一服务目录结构中。

向导只使用数字、文字输入和 Y/N 确认，无图形界面、方向键或外部编辑器。先将新文件上传到服务目录下的 `incoming/`，再运行向导：

1. 菜单 2 导入新模组，按 Fabric ID 找到并备份旧 JAR，保留其他模组。
2. 菜单 3 导入配置/资源包，填写其客户端相对路径；菜单 4 选择全部需要持续同步的配置/资源包。
3. 菜单 5 按数字选择配置，以行号替换、插入或删除文本，Y/N 确认保存。JSON 会展开显示并校验语法；其他文本配置保留原有格式，需自行核对对应模组的格式要求。修改的是发布快照，不是运行中 MC 的配置。
4. 菜单 6 停止分发某个模组，备份并移走发布源中的 JAR，玩家已有文件保留；菜单 7 可查 mod ID。
5. 菜单 1 预览；菜单 8 再次预览并询问名称，最后回答 Y 才发布。发布序号自动递增，无需手填。

向导从上次发布清单继承同步配置，保存在服务目录的 `.publish-draft.json`。菜单 0 或中断退出保留草稿，不会自动发布；重新运行可继续。源文件的修改前备份在 `admin-backups/`，不通过 HTTP 公开。发布前取消不会改变当前版本；已编辑的发布源仍保留，需撤销时从相应备份还原。配置整文件覆盖，不合并个人设置；不要把密码、数据库配置等私密内容加入分发。更新器自身配置受保护。

`python3 tools/publish_update.py --preview` 只读查看差异。直接手动执行下面的 `publish` 命令时，仍需自行逐项填写所有 `--extra`，它不会读取向导草稿。

### 手动发布与首次部署

需要 Python 3.10+ 和支持 Ed25519 的 OpenSSL 3。以下路径为示例。签名密钥存放在公开目录之外，客户端只分发公钥。

```sh
python3 tools/update_host.py keygen --key /srv/private/mirrortree-release.pem
python3 tools/update_host.py publish --source /srv/client-source --root /srv/updates/public --key /srv/private/mirrortree-release.pem --release AnotherAutumn-r1 --sequence 1
python3 tools/update_host.py serve --root /srv/updates/public --bind 0.0.0.0 --port 18881
```

`client-source` 是待发布客户端文件的快照，包含其 `mods/`。将服务设置为常驻进程；通过 frps/frpc 部署时，把外部端口转发到业务主机 `18881/TCP`，客户端 `serverUrl` 填写外部地址。`/health` 返回服务状态。

后续修改快照中的模组后，再执行 `publish`，使用更大的 `--sequence` 和新的发布名称。需要回滚时发布旧文件的新序号。示例附加选项：`--extra config/public-settings.json --extra resourcepacks/example.zip`。默认跳过源目录中标记为仅服务端的模组，拒绝重复 mod ID。

发布使用不可变内容和原子切换的版本指针。客户端固定公钥验证清单签名、拒绝旧序号回放，并对每个下载校验大小、SHA-256、Fabric ID 和版本。HTTP 可用于固定端口；也支持自行配置的 HTTPS。路径只允许显式的 mods/config/resourcepacks 文件，拒绝符号链接和目录越界。

## 下载和应用过程

每次发布的是完整版本清单，下载按文件增量进行：客户端根据实际 SHA-256 比较，仅下载缺少或内容不同的文件。一个 JAR 或配置变化时下载整个文件，不使用二进制差分或 JSON 字段合并。未变化的文件不下载；同版本号下改了内容也能识别。服务器按哈希复用相同文件。新玩家下载清单中自己缺少的文件；落后多个版本的玩家可直接对齐最新清单。

客户端每次启动后台检查，也可主菜单手动检查；没有定时轮询、在线推送或强制踢出/阻止进服。发现更新自动显示确认页，玩家点击「更新并重启」后才下载。完整下载、验签和校验成功后才生成暂存计划；独立进程等待原游戏退出，复核现有文件并备份，再替换过期文件，成功后自动重新启动游戏以加载新版模组和配置。失败的下载不应用；应用异常会尝试回滚并停止重启，中断事务会在下次应用时恢复。

清单外的文件都保留。同一个 Fabric mod ID 不能通过换文件名成为额外模组：服务器正在发布这个 ID 时，其旧文件会随升级被替换；不同 mod ID 的用户模组不受影响。发布路径被另一个 mod ID 占用时，更新会报错而不是覆盖用户模组。普通退出应用之前已暂存的版本；若管理员此后又发布了新版，玩家重启后会再次检查新版。本工具目前不分发数据包，也不编辑本地世界的 `datapacks`；多人服务器的数据包由服务器存档管理。

此模块已包含于仓库客户端发行包；完整安装步骤见[安装说明](../docs/INSTALLATION.md)，源码来源见[UPSTREAM](../docs/UPSTREAM.md)，开发规则见 [AGENTS.md](AGENTS.md)。
