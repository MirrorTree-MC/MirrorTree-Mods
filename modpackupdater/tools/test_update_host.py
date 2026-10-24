import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from urllib.request import urlopen
from urllib.error import HTTPError
from zipfile import ZipFile

import update_host as host


class HostChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / 'client'
        (self.source / 'mods').mkdir(parents=True)
        with ZipFile(self.source / 'mods/test.jar', 'w') as jar:
            jar.writestr('fabric.mod.json', json.dumps({'id': 'testmod', 'version': '1', 'environment': '*'}))
        self.key = self.root / 'private/signing.pem'
        host.keygen(self.key)
        self.hosted = self.root / 'hosted'

    def tearDown(self):
        self.temp.cleanup()

    def test_signed_publication_and_real_http(self):
        manifest = host.publish(self.source, self.hosted, self.key, 'release', 1)
        release = (self.hosted / 'latest').read_text().strip()
        raw = (self.hosted / 'releases' / release / 'manifest.json').read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), release)
        pub = self.root / 'public.der'
        pub.write_bytes(base64.b64decode(host.public_key(self.key)))
        subprocess.run(['openssl', 'pkeyutl', '-verify', '-pubin', '-keyform', 'DER', '-inkey', str(pub), '-rawin',
                        '-in', str(self.hosted / 'releases' / release / 'manifest.json'),
                        '-sigfile', str(self.hosted / 'releases' / release / 'manifest.sig')], check=True)
        server = host.UpdateServer(('127.0.0.1', 0), self.hosted)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{server.server_port}'
        try:
            self.assertIn(b'ready', urlopen(base + '/health').read())
            self.assertEqual(urlopen(base + '/v1/latest').read().decode().strip(), release)
            self.assertEqual(urlopen(base + f'/v1/releases/{release}/manifest.json').read(), raw)
            file = manifest['files'][0]
            downloaded = urlopen(base + '/v1/files/' + file['sha256']).read()
            self.assertEqual(hashlib.sha256(downloaded).hexdigest(), file['sha256'])
            for path in ('/', '/private/signing.pem', '/v1/files/../../private/signing.pem', '/v1/files/%2e%2e', '/v1/latest?key=1'):
                with self.assertRaises(HTTPError): urlopen(base + path)
            # Even a locally introduced symlink is never exposed.
            blob = self.hosted / 'files' / file['sha256']
            blob.unlink()
            blob.symlink_to(self.key)
            with self.assertRaises(HTTPError): urlopen(base + '/v1/files/' + file['sha256'])
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_sequence_and_existing_key_protection(self):
        host.publish(self.source, self.hosted, self.key, 'one', 4)
        with self.assertRaises(ValueError): host.publish(self.source, self.hosted, self.key, 'rollback', 3)
        with self.assertRaises(ValueError): host.keygen(self.key)
        self.assertEqual(self.key.stat().st_mode & 0o777, 0o600)

    def test_duplicate_ids_and_path_policy(self):
        shutil.copy(self.source / 'mods/test.jar', self.source / 'mods/duplicate.jar')
        with self.assertRaises(ValueError): host.publish(self.source, self.hosted, self.key, 'bad', 1)
        for path in ('../secret', 'config/modpackupdater.json', '/tmp/test.jar', 'saves/a', 'mods\\file.jar'):
            with self.assertRaises(ValueError): host.relative_file(self.source, path)

    def test_server_only_omitted_and_source_symlink_refused(self):
        with ZipFile(self.source / 'mods/server.jar', 'w') as jar:
            jar.writestr('fabric.mod.json', json.dumps({'id': 'servermod', 'version': '1', 'environment': 'server'}))
        result = host.publish(self.source, self.hosted, self.key, 'client', 1)
        self.assertEqual([x['modId'] for x in result['files']], ['testmod'])
        (self.source / 'mods/linked.jar').symlink_to(self.source / 'mods/test.jar')
        with self.assertRaises(ValueError): host.publish(self.source, self.hosted, self.key, 'bad', 2)


if __name__ == '__main__': unittest.main()
