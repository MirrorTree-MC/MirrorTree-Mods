#!/usr/bin/env python3
"""Publish signed client releases and serve immutable files without a cloud provider."""
import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from zipfile import ZipFile

SHA = re.compile(r"[a-f0-9]{64}\Z")
MOD_ID = re.compile(r"[a-z][a-z0-9_-]{1,63}\Z")


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def relative_file(source, name, require_file=True):
    rel = PurePosixPath(name)
    if not name or name != rel.as_posix() or "\\" in name or ":" in name or rel.is_absolute() or ".." in rel.parts:
        raise ValueError(f"Invalid instance path: {name}")
    if not ((len(rel.parts) == 2 and rel.parts[0] == "mods" and name.endswith(".jar"))
            or (len(rel.parts) == 2 and rel.parts[0] == "resourcepacks" and name.endswith(".zip"))
            or (len(rel.parts) >= 2 and rel.parts[0] == "config" and name.lower() != "config/modpackupdater.json")):
        raise ValueError(f"Protected instance path: {name}")
    p = source
    for part in rel.parts:
        p /= part
        if p.is_symlink():
            raise ValueError(f"Symlink is not publishable: {p}")
    if require_file and not p.is_file():
        raise ValueError(f"Not a file: {p}")
    return p


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".publish-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def public_key(key):
    return base64.b64encode(subprocess.check_output(
        ["openssl", "pkey", "-in", str(key), "-pubout", "-outform", "DER"])).decode()


def keygen(key):
    if key.exists():
        raise ValueError("Signing key already exists; refusing replacement")
    key.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(fd)
    try:
        subprocess.run(["openssl", "genpkey", "-algorithm", "ED25519", "-out", str(key)], check=True)
    except BaseException:
        key.unlink(missing_ok=True)
        raise
    print("trustedPublicKey=" + public_key(key))


def collect_files(source, extras=(), removals=()):
    """Validate a complete snapshot without changing the published release."""
    if removals:
        raise ValueError('Automatic client removal is disabled; unpublish the mod to retain player additions')
    paths = ["mods/" + p.name for p in sorted((source / "mods").glob("*.jar"))] + list(extras)
    if not paths:
        raise ValueError("No client mods to publish")
    entries, identities, names = [], set(), set()
    for name in paths:
        if name.lower() in names:
            raise ValueError(f"Duplicate path: {name}")
        names.add(name.lower())
        path = relative_file(source, name)
        entry = {"path": name, "sha256": digest(path), "size": path.stat().st_size}
        if entry["size"] > 1024**3:
            raise ValueError(f"File exceeds client size limit: {name}")
        if name.startswith("mods/"):
            with ZipFile(path) as z:
                meta = json.loads(z.read("fabric.mod.json"))
            if meta.get("environment") == "server":
                continue
            if not isinstance(meta.get("id"), str) or not MOD_ID.fullmatch(meta["id"]) or meta["id"] in identities:
                raise ValueError(f"Invalid/duplicate Fabric ID: {meta.get('id')}")
            if not isinstance(meta.get("version"), str) or not meta["version"].strip():
                raise ValueError(f"Invalid Fabric version: {name}")
            identities.add(meta["id"])
            entry.update(modId=meta["id"], version=meta["version"])
        entries.append(entry)
    if not entries or len(entries) > 4096 or sum(x["size"] for x in entries) > 8 * 1024**3:
        raise ValueError("Empty or oversized release")
    return entries


def publish(source, root, key, release, sequence, extras=(), removals=()):
    source, root = source.resolve(), root.resolve()
    if not release or len(release) > 200 or sequence < 0:
        raise ValueError("Invalid release label or sequence")
    if (root / "latest").exists():
        previous = (root / "latest").read_text().strip()
        m = json.loads((root / "releases" / previous / "manifest.json").read_text())
        if sequence <= m["sequence"]:
            raise ValueError("Sequence must increase for every publication, including rollbacks")
    entries = collect_files(source, extras, removals)
    for entry in entries:
        path = relative_file(source, entry["path"])
        blob = root / "files" / entry["sha256"]
        if blob.exists():
            if blob.is_symlink() or digest(blob) != entry["sha256"]:
                raise ValueError(f"Hosted content is damaged: {blob}")
        else:
            blob.parent.mkdir(parents=True, exist_ok=True)
            fd, temp = tempfile.mkstemp(prefix=".blob-", dir=blob.parent)
            try:
                with os.fdopen(fd, "wb") as output, path.open("rb") as inp:
                    shutil.copyfileobj(inp, output)
                if digest(Path(temp)) != entry["sha256"]:
                    raise ValueError(f"Source changed during publishing: {path}")
                os.replace(temp, blob)
            finally:
                if os.path.exists(temp): os.unlink(temp)
    manifest = {"schemaVersion": 1, "minecraft": "26.1.2", "release": release,
                "sequence": sequence, "files": entries, "removeModIds": list(removals)}
    raw = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
    if len(raw) > 1_000_000:
        raise ValueError("Manifest exceeds client size limit")
    release_id = hashlib.sha256(raw).hexdigest()
    directory = root / "releases" / release_id
    directory.mkdir(parents=True, exist_ok=True)
    atomic_write(directory / "manifest.json", raw)
    signature = subprocess.check_output(["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", str(key),
                                         "-in", str(directory / "manifest.json")])
    if len(signature) != 64:
        raise ValueError("Expected an Ed25519 signing key")
    atomic_write(directory / "manifest.sig", signature)
    atomic_write(root / "latest", (release_id + "\n").encode())
    print(f"Published {release}: {len(entries)} files, {sum(x['size'] for x in entries)} bytes, sequence {sequence}")
    return manifest


class UpdateServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, root):
        self.root = Path(root).resolve()
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(address, UpdateHandler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            try: request.sendall(b"HTTP/1.1 503 Busy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            finally: self.shutdown_request(request)
            return
        try: super().process_request(request, address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try: super().process_request_thread(request, address)
        finally: self.slots.release()


class UpdateHandler(BaseHTTPRequestHandler):
    server_version = "MirrorTreeUpdate/1"
    def setup(self):
        super().setup()
        self.connection.settimeout(20)

    def do_GET(self):
        self.respond(False)

    def do_HEAD(self):
        self.respond(True)

    def respond(self, head):
        if self.path == "/health":
            data = b"MirrorTree update service ready\n"
            self.send_response(200)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            if not head: self.wfile.write(data)
            return
        parts = self.path.split("/")
        root = self.server.root
        if self.path == "/v1/latest":
            path, mime, cache = root / "latest", "text/plain", "no-store"
        elif len(parts) == 5 and parts[:3] == ["", "v1", "releases"] and SHA.fullmatch(parts[3]) and parts[4] in ("manifest.json", "manifest.sig"):
            path, mime, cache = root / "releases" / parts[3] / parts[4], "application/octet-stream", "public, max-age=31536000, immutable"
        elif len(parts) == 4 and parts[:3] == ["", "v1", "files"] and SHA.fullmatch(parts[3]):
            path, mime, cache = root / "files" / parts[3], "application/octet-stream", "public, max-age=31536000, immutable"
        else:
            self.send_error(404)
            return
        if not path.is_file() or any(p.is_symlink() for p in (path, *path.parents)):
            self.send_error(404)
            return
        try:
            with path.open("rb") as stream:
                self.send_response(200)
                self.send_header("Content-Type", mime)
                self.send_header("Content-Length", str(os.fstat(stream.fileno()).st_size))
                self.send_header("Cache-Control", cache)
                self.send_header("X-Content-Type-Options", "nosniff")
                self.end_headers()
                if not head: shutil.copyfileobj(stream, self.wfile, 65536)
        except (BrokenPipeError, ConnectionResetError, TimeoutError): pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    keys = sub.add_parser("keygen")
    keys.add_argument("--key", type=Path, required=True)
    pub = sub.add_parser("publish")
    pub.add_argument("--source", type=Path, required=True)
    pub.add_argument("--root", type=Path, required=True)
    pub.add_argument("--key", type=Path, required=True)
    pub.add_argument("--release", required=True)
    pub.add_argument("--sequence", type=int, required=True)
    pub.add_argument("--extra", action="append", default=[])
    pub.add_argument("--remove-mod", action="append", default=[], help=argparse.SUPPRESS)
    serve = sub.add_parser("serve")
    serve.add_argument("--root", type=Path, required=True)
    serve.add_argument("--bind", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=18881)
    args = parser.parse_args()
    if args.action == "keygen": keygen(args.key)
    elif args.action == "publish": publish(args.source, args.root, args.key, args.release, args.sequence, args.extra, args.remove_mod)
    else:
        server = UpdateServer((args.bind, args.port), args.root)
        print(f"Update service on {args.bind}:{args.port}, root={server.root}", flush=True)
        try: server.serve_forever()
        finally: server.server_close()


if __name__ == "__main__": main()
