import json
import tempfile
import time
import unittest
from pathlib import Path
from zipfile import ZipFile

import publish_update as wizard
import update_host as host


def jar(path, identity, version):
    path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(path, 'w') as output:
        output.writestr('fabric.mod.json', json.dumps({'id': identity, 'version': version}))


class WizardChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.source = self.base / 'client-source'
        self.root = self.base / 'public'
        self.key = self.base / 'private/key.pem'
        host.keygen(self.key)
        jar(self.source / 'mods/old.jar', 'example', '1')
        jar(self.source / 'mods/unrelated.jar', 'unrelated', '1')
        (self.source / 'config').mkdir()
        (self.source / 'config/settings.json').write_text('{"value":1}')
        host.publish(self.source, self.root, self.key, 'previous', int(time.time()) + 100,
                     ['config/settings.json'])
        self.original = (self.root / 'latest').read_bytes()
        self.messages = []

    def tearDown(self):
        self.temp.cleanup()

    def create(self, answers=()):
        responses = iter(answers)
        return wizard.Wizard(self.base, self.key, ask=lambda _: next(responses), say=self.messages.append)

    def test_guided_import_and_publish_keeps_configuration_and_other_mods(self):
        incoming = self.base / 'incoming'
        jar(incoming / 'new-name.jar', 'example', '2')
        (incoming / 'new.json').write_text('{"value":2}')
        self.create(['2', 'new-name.jar', '3', 'new.json', 'config/new.json', '8', '', 'Y']).run()
        _, result = wizard.current_release(self.root)
        entries = {x['path']: x for x in result['files']}
        self.assertEqual(entries['mods/new-name.jar']['version'], '2')
        self.assertNotIn('mods/old.jar', entries)
        self.assertTrue(
            any('替换旧文件 mods/old.jar' in message for message in self.messages)
        )
        self.assertIn('mods/unrelated.jar', entries)
        self.assertIn('config/settings.json', entries)
        self.assertIn('config/new.json', entries)
        self.assertEqual(result['removeModIds'], [])
        self.assertGreater(result['sequence'], int(time.time()) + 90)
        backups = list((self.base / 'admin-backups').glob('*/mods/old.jar'))
        self.assertEqual(len(backups), 1)
        self.assertEqual(wizard.mod_metadata(backups[0])['version'], '1')
        self.assertNotEqual((self.root / 'latest').read_bytes(), self.original)

    def test_cancel_keeps_live_release_and_resumes_draft(self):
        jar(self.base / 'incoming/new.jar', 'example', '2')
        self.create(['2', 'new.jar', '8', '', 'NO', '0']).run()
        self.assertEqual((self.root / 'latest').read_bytes(), self.original)
        resumed = self.create()
        self.assertIn('config/settings.json', resumed.extras)
        self.assertTrue(resumed.preview()[1])

    def test_config_protection_and_invalid_json_leave_snapshot_intact(self):
        invalid = self.base / 'broken.json'
        invalid.write_text('{')
        app = self.create()
        with self.assertRaises(ValueError): app.import_extra(invalid, 'config/settings.json')
        for name in ('config/modpackupdater.json', 'config/./modpackupdater.json', 'config//modpackupdater.json',
                     'config/../../escape.json', 'mods/not-config.jar'):
            with self.assertRaises(ValueError): app.import_extra(invalid, name)
        self.assertEqual((self.source / 'config/settings.json').read_text(), '{"value":1}')
        self.assertEqual((self.root / 'latest').read_bytes(), self.original)

    def test_stop_distribution_never_emits_client_removals(self):
        app = self.create(['', 'Y'])
        app.retire('example')
        self.assertTrue(app.publish())
        self.assertTrue(
            any('停止同步，客户端保留 mods/old.jar' in message for message in self.messages)
        )
        resumed = self.create(['', 'Y'])
        self.assertFalse(resumed.publish())  # Unchanged snapshot does not create a new release.
        new_config = self.base / 'new.json'
        new_config.write_text('{"value":3}')
        resumed.import_extra(new_config, 'config/settings.json')
        self.assertTrue(resumed.publish())
        _, result = wizard.current_release(self.root)
        self.assertEqual(result['removeModIds'], [])
        self.assertFalse((self.source / 'mods/old.jar').exists())
        self.assertTrue((self.source / 'mods/unrelated.jar').exists())

    def test_import_filename_collision_preserves_both_mods(self):
        path = self.base / 'incoming/unrelated.jar'
        jar(path, 'example', '2')
        with self.assertRaises(ValueError): self.create().import_mod(path)
        self.assertEqual(wizard.mod_metadata(self.source / 'mods/old.jar')['version'], '1')
        self.assertEqual(wizard.mod_metadata(self.source / 'mods/unrelated.jar')['id'], 'unrelated')

    def test_numbered_configuration_edit_rejects_invalid_json_and_confirms_save(self):
        app = self.create(['1', '1', '2', 'invalid', '4', '1', '2', '  "value": 7', '4', 'Y'])
        app.edit_config()
        self.assertEqual(json.loads((self.source / 'config/settings.json').read_text()), {'value': 7})
        self.assertTrue(any('JSON 格式有误' in message for message in self.messages))
        backups = list((self.base / 'admin-backups').glob('*/config/settings.json'))
        self.assertEqual(len(backups), 1)
        self.assertEqual(json.loads(backups[0].read_text()), {'value': 1})
        self.assertEqual((self.root / 'latest').read_bytes(), self.original)
        self.assertEqual(list(self.base.glob('.config-edit-*')), [])

    def test_cancel_numbered_edit_leaves_configuration_intact(self):
        self.create(['1', '1', '2', '  "value": 9', '0']).edit_config()
        self.assertEqual((self.source / 'config/settings.json').read_text(), '{"value":1}')


if __name__ == '__main__':
    unittest.main()
