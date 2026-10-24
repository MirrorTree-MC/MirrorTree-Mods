# ModpackUpdater · 26.1.2

面向 Minecraft 26.1.2 / Fabric / Java 25 的客户端更新器，保留 `modpackupdater` ID 和 `config/modpackupdater.json`。更新文件直接来自管理员自己的服务器固定 HTTP(S) 端口。服务器不需要安装此模组；[独立更新服务](tools/update_host.py)可以和 Minecraft 同机运行，更新发布不需要重启 MC。

启动后在后台检查。主菜单右上角「整合包更新」显示可用更新数量，打开后可预览模组名称、版本和下载大小。点击「下载更新」会暂存全部文件并校验；之后点击「退出并应用」，或正常退出游戏，独立更新进程会等待游戏关闭再替换文件。稍等应用完成后重新从启动器打开游戏。网络失败会显示错误并保留当前整合包，可手动重试。

配置示例：

```json
{
  "serverUrl": "http://your-server.example:18881/",
  "trustedPublicKey": "由管理员提供的 Ed25519 DER 公钥，使用 Base64 编码",
  "checkOnStartup": true
}
```

主菜单允许手动检查和取消暂存更新。后台检查不会自动下载。旧 `updateUrl` 仅能迁移到具备签名和新协议的服务；旧版未签名云清单不再自动执行。原版本记录文件保留，比较依据改为实际文件 SHA-256，支持相同版本号下的修复以及更新器自身更新。

更新按 Fabric mod ID 定位旧版，即使 JAR 文件名变化也能替换。清单外的额外模组保留；管理员可用 `--remove-mod` 明确退役某个模组。默认只发布 `mods`；需要分发公开配置或资源包时逐项使用 `--extra`。更新器的服务器地址和信任公钥配置受保护。

替换前的文件保存在实例的 `mod-update-backups/updater-*`；下载和事务日志在 `.modpackupdater/`。若上一次应用中断，独立应用器先恢复备份，再处理待应用计划。应用日志在 `.modpackupdater/apply.log`。无法启动游戏时也可以用 Java 25 手动运行已提取的应用器：

```sh
java -jar /path/to/instance/.modpackupdater/apply-helper.jar /path/to/instance 0
```

执行这条恢复命令前关闭该实例；最后的 `0` 表示不等待其他进程。路径/备份损坏或本地文件在下载后变化时，应用器会停止并保留恢复信息。

## 自托管更新服务

需要 Python 3.10+ 和支持 Ed25519 的 OpenSSL 3。以下路径为示例。签名密钥存放在公开目录之外，客户端只分发公钥。

```sh
python3 tools/update_host.py keygen --key /srv/private/mirrortree-release.pem
python3 tools/update_host.py publish --source /srv/client-source --root /srv/updates/public --key /srv/private/mirrortree-release.pem --release AnotherAutumn-r1 --sequence 1
python3 tools/update_host.py serve --root /srv/updates/public --bind 0.0.0.0 --port 18881
```

`client-source` 是待发布客户端文件的快照，包含其 `mods/`。将服务设置为常驻进程；通过 frps/frpc 部署时，把外部端口转发到业务主机 `18881/TCP`，客户端 `serverUrl` 填写外部地址。`/health` 返回服务状态。

后续修改快照中的模组后，再执行 `publish`，使用更大的 `--sequence` 和新的发布名称。需要回滚时发布旧文件的新序号。示例附加选项：`--extra config/public-settings.json --extra resourcepacks/example.zip --remove-mod retired_mod`。默认跳过源目录中标记为仅服务端的模组，拒绝重复 mod ID。

发布使用不可变内容和原子切换的版本指针。客户端固定公钥验证清单签名、拒绝旧序号回放，并对每个下载校验大小、SHA-256、Fabric ID 和版本。HTTP 可用于固定端口；也支持自行配置的 HTTPS。路径只允许显式的 mods/config/resourcepacks 文件，拒绝符号链接和目录越界。

此模块已包含于仓库客户端发行包；完整安装步骤见[安装说明](../docs/INSTALLATION.md)，源码来源见[UPSTREAM](../docs/UPSTREAM.md)，开发规则见 [AGENTS.md](AGENTS.md)。
