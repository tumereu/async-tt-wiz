"""Actual migration bytes in image-format fixtures; no Docker/device lifecycle."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from e2e import environment as e


# Independent expected inventory, not copied from the implementation or observed DB.
BASE_STORES = {
    'account', 'account_session', 'password_reset_token', 'reset_mail', 'definition',
    'account_definition', 'saved_deck', 'deck_entry', 'request_identity', 'account_request',
    'contact', 'game', 'invitation', 'game_inbox', 'game_outbox', 'game_revision',
    'game_stream', 'player_preparation', 'game_definition', 'game_play',
}
BASE_VERSIONS = [str(i) for i in range(1, 10)]
CHAT = 'V13__game_chat.sql'
CONSOLIDATED_STORES = {'gameplay_group', 'gameplay_mutation', 'game_play_mutation',
                       'activity_cursor', 'account_activity', 'activity_summary',
                        'activity_installation', 'activity_push', 'gameplay_tree_control'}
CONSOLIDATED_SCRIPTS = {'V11__gameplay_mutations.sql', 'V14__returned_lobby_incarnations.sql',
                         'V15__activity_notifications.sql', 'V16__retained_gameplay_tree.sql'}
SOURCE = e.ROOT / 'server/src/main/resources/db/migration'


class ResetFixture:
    def __init__(self, root, chat=True, consolidated=False):
        self.root = root
        self.source = root / 'server/src/main/resources/db/migration'
        self.source.mkdir(parents=True)
        self.scripts = {p.name: p.read_bytes() for p in SOURCE.glob('*.sql')
                        if int(p.name.split('__')[0][1:]) <= 9 or (p.name == CHAT and chat) or
                        (consolidated and p.name in CONSOLIDATED_SCRIPTS)}
        for name, content in self.scripts.items():
            (self.source / name).write_bytes(content)
        self.jar = root / 'server/build/install/server/lib/server-0.1.0.jar'
        self.jar.parent.mkdir(parents=True)
        self.manifest = root / 'server/build/application-image.json'
        self.value = {'project': 'owned', 'directory': str(root),
                      'images': {'server': 'sha256:' + '1' * 64}, 'inputs': {}}
        self.server = {'imageId': self.value['images']['server'], 'platform': 'linux/amd64',
                       'sourceCommit': '2' * 40}
        self.package(self.scripts)
        self.resources = {service: {'Image': self.server['imageId']} for service in ('backend', 'migrate')}
        self.stores = BASE_STORES | ({'game_chat'} if chat else set())
        if consolidated:
            self.stores |= CONSOLIDATED_STORES
        self.tables = set(self.stores) | {'flyway_schema_history', 'service_writer'}
        self.versions = BASE_VERSIONS + (['11'] if consolidated else []) + (['13'] if chat else []) + (['14', '15', '16'] if consolidated else [])
        self.dirty = None
        self.counts = set()
        self.writer = '1'
        self.state = dict(instance='owned', versions={'prism': 1, 'quilt': 1}, generation=0,
                          messageCount=0, sourceFault='ok', mailFault='ok')
        self.messages = []

    def pin_manifest(self):
        self.manifest.write_text(json.dumps(self.server))
        self.value['inputs']['server/build/application-image.json'] = e.sha(self.manifest)

    def package(self, scripts):
        with zipfile.ZipFile(self.jar, 'w') as archive:
            for name, content in scripts.items():
                archive.writestr('db/migration/' + name, content)
        self.server['libraries'] = {self.jar.name: e.sha(self.jar)}
        distribution = hashlib.sha256(json.dumps(self.server['libraries'], sort_keys=True).encode()).hexdigest()
        self.server['distributionSha256'] = distribution
        self.image = {'Id': self.server['imageId'], 'Os': 'linux', 'Architecture': 'amd64',
                      'Config': {'Labels': {'org.asyncttwiz.distribution.sha256': distribution,
                                            'org.opencontainers.image.revision': self.server['sourceCommit']}}}
        self.pin_manifest()

    def database(self, value, sql):
        if sql == "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'":
            return ','.join(sorted(self.tables))
        if sql == 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank':
            return '\n'.join(self.versions)
        if sql == 'SELECT count(*) FROM service_writer WHERE singleton AND epoch > 0':
            return self.writer
        table = sql.removeprefix('SELECT count(*) FROM ')
        if sql != 'SELECT count(*) FROM ' + table or table not in self.stores:
            raise AssertionError('Unexpected query: ' + sql)
        self.counts.add(table)
        return '1' if table == self.dirty else '0'

    def admin(self, value, path):
        if path == '/admin/state':
            return self.state
        if path == '/admin/messages':
            return self.messages
        raise AssertionError('Unexpected administration request')


class GameResetBaselineTest(unittest.TestCase):
    def fixture(self, chat=True, consolidated=False):
        temporary = tempfile.TemporaryDirectory(dir='/tmp/opencode')
        self.addCleanup(temporary.cleanup)
        fixture = ResetFixture(Path(temporary.name), chat, consolidated)
        for name, replacement in [('ROOT', fixture.root), ('owned', lambda _: fixture.resources),
                                  ('docker', lambda *args: json.dumps([fixture.image])),
                                  ('database', fixture.database), ('admin', fixture.admin)]:
            patcher = patch.object(e, name, replacement)
            patcher.start()
            self.addCleanup(patcher.stop)
        return fixture

    def test_baseline_and_current_actual_sql_require_every_store_empty(self):
        for chat in (False, True):
            with self.subTest(chat=chat):
                fixture = self.fixture(chat)
                result = e.empty_baseline(fixture.value)
                self.assertEqual(fixture.stores, fixture.counts)
                self.assertEqual(fixture.tables, set(result['tables']))
                self.assertEqual(BASE_VERSIONS + (['13'] if chat else []), result['migrations'])
                self.assertEqual(fixture.server['imageId'], result['schemaIdentity']['imageId'])
                self.assertEqual({name: hashlib.sha256(data).hexdigest() for name, data in fixture.scripts.items()},
                                 result['schemaIdentity']['migrationSha256'])
                for dirty in sorted(fixture.stores):
                    with self.subTest(dirty=dirty), self.assertRaisesRegex(e.Refusal, 'Retained private baseline'):
                        fixture.dirty = dirty
                        e.empty_baseline(fixture.value)

    def test_missing_extra_or_renamed_chat_table_is_refused(self):
        fixture = self.fixture()
        for tables in (fixture.tables - {'game_chat'}, fixture.tables | {'unknown_store'},
                       (fixture.tables - {'game_chat'}) | {'game_chat_wrong'}):
            with self.subTest(tables=tables), self.assertRaisesRegex(e.Refusal, 'Unexpected baseline tables'):
                fixture.tables = tables
                e.empty_baseline(fixture.value)

    def test_consolidated_exact_sql_stores_versions_and_each_dirty_store(self):
        fixture = self.fixture(chat=True, consolidated=True)
        self.assertEqual(BASE_VERSIONS + ['11', '13', '14', '15', '16'], fixture.versions)
        self.assertEqual(BASE_STORES | CONSOLIDATED_STORES | {'game_chat'}, fixture.stores)
        result = e.empty_baseline(fixture.value)
        self.assertEqual(fixture.stores, fixture.counts)
        self.assertEqual(fixture.versions, result['migrations'])
        for table in sorted(CONSOLIDATED_STORES | {'game_chat'}):
            with self.subTest(dirty=table), self.assertRaisesRegex(e.Refusal, 'Retained private baseline'):
                fixture.dirty = table
                e.empty_baseline(fixture.value)
        fixture.dirty = None
        original = fixture.tables
        for table in sorted(CONSOLIDATED_STORES | {'game_chat'}):
            fixture.tables = original - {table}
            with self.subTest(missing=table), self.assertRaisesRegex(e.Refusal, 'Unexpected baseline tables'):
                e.empty_baseline(fixture.value)
        fixture.tables = original
        for versions in (BASE_VERSIONS + ['11', '14', '15'], BASE_VERSIONS + ['11', '13', '15'],
                         BASE_VERSIONS + ['10', '11', '13', '14', '15'],
                         BASE_VERSIONS + ['11', '12', '13', '14', '15'],
                          BASE_VERSIONS + ['11', '13', '14', '15']):
            fixture.versions = versions
            with self.subTest(versions=versions), self.assertRaisesRegex(e.Refusal, 'Unexpected migration baseline'):
                e.empty_baseline(fixture.value)

    def test_each_consolidated_migration_requires_exact_source_bytes_before_database(self):
        fixture = self.fixture(chat=True, consolidated=True)
        for name in sorted(CONSOLIDATED_SCRIPTS | {CHAT}):
            original = fixture.scripts[name]
            changed = original + b'\n-- unqualified edit\n'
            (fixture.source / name).write_bytes(changed)
            fixture.package(dict(fixture.scripts, **{name: changed}))
            with self.subTest(migration=name), patch.object(e, 'database') as database, self.assertRaisesRegex(e.Refusal, 'Changed reset migration source'):
                e.empty_baseline(fixture.value)
            database.assert_not_called()
            (fixture.source / name).write_bytes(original)
        fixture.package(fixture.scripts)

    def test_current_source_matches_exact_registry_without_invented_versions(self):
        scripts = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCE.glob('*.sql')}
        self.assertEqual(BASE_VERSIONS + ['11', '13', '14', '15', '16'],
                         sorted((name.split('__')[0][1:] for name in scripts), key=int))
        self.assertEqual(scripts, {name: entry[0] for name, entry in e.RESET_MIGRATIONS.items()})

    def test_chat_table_is_unexpected_for_baseline_image(self):
        fixture = self.fixture(False)
        fixture.tables.add('game_chat')
        with self.assertRaisesRegex(e.Refusal, 'Unexpected baseline tables'):
            e.empty_baseline(fixture.value)

    def test_exact_versions_not_count_or_highest_version_are_required(self):
        fixture = self.fixture()
        for versions in (BASE_VERSIONS, BASE_VERSIONS + ['10'], BASE_VERSIONS + ['13', '14'],
                         BASE_VERSIONS + ['13', '13'], ['13'] + BASE_VERSIONS,
                         BASE_VERSIONS[1:] + ['12', '13']):
            with self.subTest(versions=versions), self.assertRaisesRegex(e.Refusal, 'Unexpected migration baseline'):
                fixture.versions = versions
                e.empty_baseline(fixture.value)

    def test_source_image_missing_extra_and_changed_chat_are_refused_before_db(self):
        fixture = self.fixture()
        cases = [dict(fixture.scripts, **{CHAT: b'CREATE TABLE game_chat_wrong ();'}),
                 {name: data for name, data in fixture.scripts.items() if name != CHAT},
                 dict(fixture.scripts, **{'V14__unknown.sql': b'CREATE TABLE unknown_store ();'})]
        for scripts in cases:
            with self.subTest(scripts=list(scripts)):
                fixture.package(scripts)  # Even a correctly re-pinned image may not disagree with source.
                with patch.object(e, 'database') as database, self.assertRaisesRegex(e.Refusal, 'source/image migration mismatch'):
                    e.empty_baseline(fixture.value)
                database.assert_not_called()

    def test_source_without_chat_cannot_qualify_current_image(self):
        fixture = self.fixture()
        (fixture.source / CHAT).unlink()
        with self.assertRaisesRegex(e.Refusal, 'source/image migration mismatch'):
            e.empty_baseline(fixture.value)

    def test_changed_source_is_refused_even_when_image_and_db_agree(self):
        fixture = self.fixture()
        fixture.scripts[CHAT] += b'\n-- unqualified edit\n'
        (fixture.source / CHAT).write_bytes(fixture.scripts[CHAT])
        fixture.package(fixture.scripts)
        with patch.object(e, 'database') as database, self.assertRaisesRegex(e.Refusal, 'Changed reset migration source'):
            e.empty_baseline(fixture.value)
        database.assert_not_called()

    def test_unimplemented_migrations_are_not_accepted_from_source_image_or_db(self):
        for version in (10, 11, 12, 14, 15, 99):
            with self.subTest(version=version):
                fixture = self.fixture()
                name = f'V{version}__unknown.sql'
                fixture.scripts[name] = b'CREATE TABLE unknown_store ();'
                (fixture.source / name).write_bytes(fixture.scripts[name])
                fixture.package(fixture.scripts)
                fixture.tables.add('unknown_store')
                fixture.versions.append(str(version))
                with patch.object(e, 'database') as database, self.assertRaisesRegex(e.Refusal, 'Unqualified reset migrations'):
                    e.empty_baseline(fixture.value)
                database.assert_not_called()

    def test_missing_required_source_migration_is_refused(self):
        fixture = self.fixture()
        (fixture.source / 'V9__initial_play.sql').unlink()
        with self.assertRaisesRegex(e.Refusal, 'Unqualified reset migrations'):
            e.empty_baseline(fixture.value)

    def test_changed_manifest_and_jar_are_refused(self):
        fixture = self.fixture()
        original = fixture.manifest.read_bytes()
        fixture.manifest.write_bytes(original + b' ')
        with self.assertRaisesRegex(e.Refusal, 'Changed reset image manifest'):
            e.empty_baseline(fixture.value)
        fixture.manifest.write_bytes(original)
        fixture.jar.write_bytes(fixture.jar.read_bytes() + b'changed')
        with self.assertRaisesRegex(e.Refusal, 'Changed reset image library'):
            e.empty_baseline(fixture.value)

    def test_manifest_image_distribution_and_runtime_mismatches_are_refused(self):
        fixture = self.fixture()
        fixture.value['images']['server'] = 'sha256:' + '3' * 64
        with self.assertRaisesRegex(e.Refusal, 'Reset image identity mismatch'):
            e.empty_baseline(fixture.value)
        fixture.value['images']['server'] = fixture.server['imageId']
        original = copy.deepcopy(fixture.image)
        for key, wrong in [('Id', 'sha256:' + '3' * 64), ('Architecture', 'arm64'),
                           ('Config', {'Labels': {}})]:
            fixture.image = dict(original, **{key: wrong})
            with self.subTest(key=key), self.assertRaisesRegex(e.Refusal, 'Reset image provenance mismatch'):
                e.empty_baseline(fixture.value)
        fixture.image = original
        fixture.server['distributionSha256'] = '0' * 64
        fixture.pin_manifest()
        with self.assertRaisesRegex(e.Refusal, 'Reset distribution identity mismatch'):
            e.empty_baseline(fixture.value)
        fixture.package(fixture.scripts)
        for service in ('backend', 'migrate'):
            fixture.resources[service]['Image'] = 'sha256:' + '3' * 64
            with self.subTest(service=service), self.assertRaisesRegex(e.Refusal, 'Reset runtime image mismatch'):
                e.empty_baseline(fixture.value)
            fixture.resources[service]['Image'] = fixture.server['imageId']

    def test_extra_library_and_duplicate_packaged_migrations_are_refused(self):
        fixture = self.fixture()
        extra = fixture.jar.with_name('extra.jar')
        extra.write_bytes(fixture.jar.read_bytes())
        with self.assertRaisesRegex(e.Refusal, 'Changed reset library inventory'):
            e.empty_baseline(fixture.value)
        fixture.server['libraries'][extra.name] = e.sha(extra)
        fixture.server['distributionSha256'] = hashlib.sha256(json.dumps(fixture.server['libraries'], sort_keys=True).encode()).hexdigest()
        fixture.image['Config']['Labels']['org.asyncttwiz.distribution.sha256'] = fixture.server['distributionSha256']
        fixture.pin_manifest()
        with self.assertRaisesRegex(e.Refusal, 'Ambiguous packaged reset migration'):
            e.empty_baseline(fixture.value)

    def test_writer_and_all_adapter_baseline_guards_remain_required(self):
        fixture = self.fixture()
        fixture.writer = '0'
        with self.assertRaisesRegex(e.Refusal, 'Missing baseline writer'):
            e.empty_baseline(fixture.value)
        fixture.writer = '1'
        original = fixture.state.copy()
        for key, wrong in [('instance', 'foreign'), ('versions', {'prism': 2, 'quilt': 1}),
                           ('generation', 1), ('messageCount', 1), ('sourceFault', 'failed'), ('mailFault', 'failed')]:
            fixture.state = dict(original, **{key: wrong})
            with self.subTest(key=key), self.assertRaisesRegex(e.Refusal, 'Nonempty adapter baseline'):
                e.empty_baseline(fixture.value)
        fixture.state = original
        fixture.messages = [{'retained': True}]
        with self.assertRaisesRegex(e.Refusal, 'Retained baseline deliveries'):
            e.empty_baseline(fixture.value)

    def test_nonempty_chat_cannot_complete_reset_or_issue_handoff(self):
        fixture = self.fixture()
        fixture.dirty = 'game_chat'
        (fixture.root / 'ready.json').write_text('old handoff')
        with patch.object(e, 'capture'), patch.object(e, 'remove_runtime', return_value={'backend': 'old'}), \
                patch.object(e, 'fresh_services'), patch.object(e, 'stop_relay'), patch.object(e, 'wait') as wait:
            with self.assertRaises(e.Refusal):
                e.reset(fixture.value, 0)
            wait.assert_not_called()
        self.assertTrue((fixture.root / 'reset-pending.json').exists())
        self.assertFalse((fixture.root / 'ready.json').exists())
        self.assertFalse((fixture.root / 'reset-completed.json').exists())


if __name__ == '__main__': unittest.main()
