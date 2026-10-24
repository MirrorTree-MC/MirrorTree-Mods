#!/usr/bin/env python3
"""Interactive administrator workflow for a signed client update service (Python 3.10+)."""
import argparse
from datetime import datetime
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import uuid
from zipfile import ZipFile, BadZipFile

import update_host as host


def current_release(root):
    if not (root / 'latest').exists():
        return '', {'files': [], 'removeModIds': [], 'sequence': -1}
    release_id = (root / 'latest').read_text().strip()
    if not host.SHA.fullmatch(release_id):
        raise ValueError('当前发布指针损坏，请先检查 public/latest')
    manifest = root / 'releases' / release_id / 'manifest.json'
    if host.digest(manifest) != release_id:
        raise ValueError('当前发布清单校验失败')
    return release_id, json.loads(manifest.read_text())


def mod_metadata(path):
    with ZipFile(path) as jar:
        meta = json.loads(jar.read('fabric.mod.json'))
    if not isinstance(meta.get('id'), str) or not host.MOD_ID.fullmatch(meta['id']):
        raise ValueError(f'无效的 Fabric mod ID：{path.name}')
    if not isinstance(meta.get('version'), str) or not meta['version'].strip():
        raise ValueError(f'无效的模组版本：{path.name}')
    return meta


def copy_atomic(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.import-', dir=target.parent)
    try:
        with os.fdopen(fd, 'wb') as output, source.open('rb') as inp:
            shutil.copyfileobj(inp, output)
        if host.digest(Path(name)) != host.digest(source):
            raise ValueError('导入期间源文件发生变化，请上传完成后重试')
        os.replace(name, target)
    finally:
        Path(name).unlink(missing_ok=True)


class Wizard:
    def __init__(self, base, key, ask=input, say=print):
        self.base, self.key = Path(base).resolve(), Path(key).expanduser()
        self.source, self.root = self.base / 'client-source', self.base / 'public'
        self.ask, self.say = ask, say
        self.release_id, self.previous = current_release(self.root)
        self.extras = {x['path'] for x in self.previous['files'] if not x['path'].startswith('mods/')}
        self.draft = self.base / '.publish-draft.json'
        if self.draft.exists():
            state = json.loads(self.draft.read_text())
            if state.get('baseRelease') == self.release_id:
                self.extras = set(state['extras'])
            else:
                self.say('发布版本已变化，使用当前版本的配置名单；发布源中的草稿文件仍保留。')

    def save(self):
        host.atomic_write(self.draft, (json.dumps({'baseRelease': self.release_id,
            'extras': sorted(self.extras)}, ensure_ascii=False, indent=2) + '\n').encode())

    def backup(self, names):
        directory = self.base / 'admin-backups' / (datetime.now().strftime('%Y%m%d-%H%M%S-') + uuid.uuid4().hex[:8])
        for name in names:
            source = host.relative_file(self.source, name)
            target = directory / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        if names:
            self.say(f'修改前备份：{directory}')
        return directory

    def incoming(self):
        self.say(f'先用 SFTP 上传文件到 {self.base / "incoming"}，也可填写服务器上的其他文件路径。')
        value = self.ask('源文件路径（相对路径从 incoming 开始，留空返回）：').strip()
        if not value:
            return None
        path = Path(value).expanduser()
        if not path.is_absolute():
            path = self.base / 'incoming' / path
        if not path.is_file() or any(p.is_symlink() for p in (path, *path.parents)):
            raise ValueError('源文件不存在或是符号链接')
        return path

    def import_mod(self, path):
        if path.suffix != '.jar':
            raise ValueError('请选择 .jar 文件')
        meta = mod_metadata(path)
        if meta.get('environment') == 'server':
            raise ValueError('这是仅服务端模组，不应分发给客户端')
        name = 'mods/' + path.name
        target = host.relative_file(self.source, name, require_file=False)
        old = []
        for p in (self.source / 'mods').glob('*.jar'):
            checked = host.relative_file(self.source, 'mods/' + p.name)
            other = mod_metadata(checked)
            if p == target and other['id'] != meta['id']:
                raise ValueError('同名目标属于另一个模组，请更改上传文件名')
            if other['id'] == meta['id']:
                old.append('mods/' + p.name)
        backup = self.backup(old)
        try:
            copy_atomic(path, target)
            for prior in old:
                if self.source / prior != target:
                    (self.source / prior).unlink()
        except BaseException:
            if name not in old:
                target.unlink(missing_ok=True)
            for prior in old:
                copy_atomic(backup / prior, self.source / prior)
            raise
        self.save()
        self.say(f'已准备 {meta["id"]} {meta["version"]}；按 ID 替换了旧文件，尚未发布。')

    def import_extra(self, path, name):
        if not name.startswith(('config/', 'resourcepacks/')):
            raise ValueError('这里只接受 config/... 或 resourcepacks/xxx.zip')
        target = host.relative_file(self.source, name, require_file=False)
        if name.endswith('.json'):
            json.loads(path.read_text(encoding='utf-8-sig'))
        if path.resolve() != target.resolve():
            self.backup([name] if target.exists() else [])
            copy_atomic(path, target)
        self.extras.add(name)
        self.save()
        self.say(f'已准备 {name} 并加入同步名单；尚未发布。')

    def candidates(self):
        names = set(self.extras)
        for directory in ('config', 'resourcepacks'):
            for path in (self.source / directory).rglob('*'):
                if path.is_file():
                    name = path.relative_to(self.source).as_posix()
                    try:
                        host.relative_file(self.source, name)
                    except ValueError:
                        continue
                    names.add(name)
        return sorted(names)

    def select_extras(self):
        names = self.candidates()
        if not names:
            self.say('没有配置或资源包，请先使用菜单 3 导入。')
            return
        for i, name in enumerate(names, 1):
            self.say(f'{i}. [{"✓" if name in self.extras else " "}] {name}')
        self.say('这里只同步选中的文件；配置整文件覆盖，不合并玩家个人修改。不要选含密码的配置。')
        choice = self.ask('填写全部需要同步的编号，逗号分隔；留空保留，0 清空：').strip()
        if not choice:
            return
        indices = set() if choice == '0' else {int(i.strip()) for i in choice.split(',')}
        if any(i < 1 or i > len(names) for i in indices):
            raise ValueError('编号超出范围')
        self.extras = {names[i - 1] for i in indices}
        self.save()

    def edit_config(self):
        names = [n for n in self.candidates() if n.startswith('config/') and (self.source / n).is_file()]
        for i, name in enumerate(names, 1):
            self.say(f'{i}. {name}')
        choice = self.ask('要编辑的配置编号，留空返回：').strip()
        if not choice:
            return
        i = int(choice)
        if not 1 <= i <= len(names):
            raise ValueError('编号超出范围')
        name = names[i - 1]
        target = host.relative_file(self.source, name)
        original = target.read_text(encoding='utf-8-sig')
        # JSON is expanded for readable line numbers; the snapshot is untouched until confirmation.
        text = json.dumps(json.loads(original), ensure_ascii=False, indent=2) if name.endswith('.json') else original
        lines = text.splitlines()
        while True:
            for n, line in enumerate(lines, 1):
                self.say(f'{n:4}: {line}')
            self.say('1 替换一行  2 在某行前插入  3 删除一行  4 保存  0 放弃本次编辑')
            action = self.ask('请选择：').strip()
            if action == '0': return
            if action == '4':
                content = '\n'.join(lines) + '\n'
                if name.endswith('.json'):
                    try: json.loads(content)
                    except ValueError as e:
                        self.say(f'JSON 格式有误，尚未保存：{e}')
                        continue
                if self.ask('保存以上内容并加入客户端同步名单？Y/N（默认 N）：').strip().upper() != 'Y':
                    continue
                fd, draft = tempfile.mkstemp(prefix='.config-edit-', suffix=target.suffix, dir=self.base)
                try:
                    with os.fdopen(fd, 'w', encoding='utf-8') as output: output.write(content)
                    self.import_extra(Path(draft), name)
                finally:
                    Path(draft).unlink(missing_ok=True)
                return
            if action not in ('1', '2', '3'): continue
            value = self.ask(f'行号（插入可填 {len(lines) + 1} 表示末尾；留空返回）：').strip()
            if not value: continue
            try: index = int(value) - 1
            except ValueError:
                self.say('请输入数字行号。')
                continue
            if index < 0 or index >= len(lines) + (1 if action == '2' else 0):
                self.say('行号超出范围。')
                continue
            if action == '1': lines[index] = self.ask('新的一整行内容（保留所需引号和逗号）：')
            elif action == '2': lines.insert(index, self.ask('要插入的一整行内容：'))
            elif self.ask(f'确认删除第 {index + 1} 行？Y/N（默认 N）：').strip().upper() == 'Y': lines.pop(index)

    def retire(self, identity):
        if not host.MOD_ID.fullmatch(identity) or identity == 'modpackupdater':
            raise ValueError('无效的移除 ID；不能移除更新器自身')
        old = []
        for p in (self.source / 'mods').glob('*.jar'):
            checked = host.relative_file(self.source, 'mods/' + p.name)
            if mod_metadata(checked)['id'] == identity:
                old.append('mods/' + p.name)
        if not old and identity not in {x.get('modId') for x in self.previous['files']}:
            raise ValueError('发布源和上次版本都没有这个 mod ID，请核对拼写')
        self.backup(old)
        for name in old:
            (self.source / name).unlink()
        self.save()
        self.say(f'已停止分发 {identity}；发布后客户端已有文件会保留，不会远程删除。')

    def preview(self):
        for name in self.extras:
            if name.endswith('.json'):
                json.loads(host.relative_file(self.source, name).read_text(encoding='utf-8-sig'))
        entries = host.collect_files(self.source, sorted(self.extras))
        before = {x['path']: x for x in self.previous['files']}
        after = {x['path']: x for x in entries}
        changed = [x for x in entries if before.get(x['path']) != x]
        self.say(f'\n完整发布：{len(entries)} 个文件；本次新增/变化 {len(changed)} 个文件。')
        for entry in changed:
            self.say(f'  {"更新" if entry["path"] in before else "新增"} {entry["path"]} ({entry["size"]:,} 字节)')
        for name in sorted(before.keys() - after.keys()):
            entry = before[name]
            identity = entry.get('modId')
            replaced = identity and any(x.get('modId') == identity for x in entries)
            action = '替换旧文件' if replaced else '停止同步，客户端保留'
            self.say(f'  {action} {name}')
        self.say('清单外的模组、配置、资源包和本地世界数据包均保留。')
        self.say(f'变化文件共 {sum(x["size"] for x in changed) / 1048576:.2f} MiB；每位玩家只下载自己缺少或内容不同的文件。')
        return entries, bool(changed or before.keys() != after.keys() or self.previous.get('removeModIds'))

    def publish(self):
        entries, changed = self.preview()
        if not changed:
            self.say('与当前版本一致，无需发布。')
            return False
        if not self.key.is_file():
            raise ValueError(f'找不到已有签名密钥：{self.key}；请指定 --key，不要重新生成密钥')
        label = self.ask('发布名称（留空自动生成）：').strip() or datetime.now().strftime('AnotherAutumn-%Y%m%d-%H%M%S')
        if self.ask('确认发布，让玩家收到以上更新？Y/N（默认 N）：').strip().upper() != 'Y':
            self.say('已取消发布，草稿保留。')
            return False
        if current_release(self.root)[0] != self.release_id:
            raise ValueError('其他进程已发布新版本，请退出后重新运行向导')
        if host.collect_files(self.source, sorted(self.extras)) != entries:
            raise ValueError('确认期间文件发生变化，请重新预览')
        sequence = max(int(time.time()), self.previous['sequence'] + 1)
        result = host.publish(self.source, self.root, self.key, label, sequence, sorted(self.extras))
        self.release_id, self.previous = current_release(self.root)
        self.save()
        self.say(f'发布成功：{result["release"]}，序号 {sequence}。服务立即生效，无需重启。')
        return True

    def run(self):
        (self.base / 'incoming').mkdir(exist_ok=True)
        (self.source / 'mods').mkdir(parents=True, exist_ok=True)
        self.say(f'\nMirrorTree 客户端更新发布向导\n发布源：{self.source}\n这里只修改发布快照；玩家在发布确认后才会收到更新。')
        self.say('已保留上次同步配置。草稿修改前会备份；退出不会自动发布。玩家额外内容保留。')
        while True:
            self.say('\n1 预览变更  2 导入/替换模组  3 导入配置或资源包\n4 选择同步配置/资源包  5 编辑配置  6 停止分发模组\n7 查看发布源模组  8 发布更新  0 保存草稿退出')
            try:
                choice = self.ask('请选择：').strip()
                if choice == '0':
                    self.save()
                    return
                if choice == '1':
                    self.preview()
                elif choice == '2':
                    path = self.incoming()
                    if path: self.import_mod(path)
                elif choice == '3':
                    path = self.incoming()
                    if path:
                        name = self.ask('客户端目标路径，例如 config/example.json：').strip()
                        if name: self.import_extra(path, name)
                elif choice == '4': self.select_extras()
                elif choice == '5': self.edit_config()
                elif choice == '6':
                    identity = self.ask('要停止分发的 mod ID（菜单 7 可查看），留空返回：').strip()
                    if identity and self.ask(f'确认从发布源移走 {identity}，玩家已有文件保留？Y/N（默认 N）：').strip().upper() == 'Y':
                        self.retire(identity)
                elif choice == '7':
                    for entry in host.collect_files(self.source):
                        self.say(f'{entry["modId"]} {entry["version"]} · {entry["path"]}')
                elif choice == '8':
                    if self.publish(): return
            except (ValueError, OSError, KeyError, BadZipFile, subprocess.CalledProcessError) as e:
                self.say(f'操作未完成：{e}。请预览当前状态后重试。')
            except (EOFError, KeyboardInterrupt):
                self.save()
                self.say('\n已退出；草稿保留。')
                return


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=Path(__file__).resolve().parent.parent,
                        help='更新服务目录，包含 client-source/、public/、incoming/')
    parser.add_argument('--key', type=Path, default=Path.home() / '.local/share/mirrortree-updates/keys/release.pem')
    parser.add_argument('--preview', action='store_true', help='只查看差异，不编辑、不发布')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.preview:
            Wizard(args.base, args.key).preview()
            return
        args.base.mkdir(parents=True, exist_ok=True)
        with (args.base / '.publish-wizard.lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise ValueError('另一个发布向导正在运行')
            Wizard(args.base, args.key).run()
    except (ValueError, OSError, KeyError, BadZipFile) as e:
        parser.exit(1, f'无法继续：{e}\n')


if __name__ == '__main__':
    main()
