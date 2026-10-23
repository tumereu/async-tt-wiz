"""The account schema must join the existing independent-scenario reset oracle."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from e2e import environment as e
from test_environment_game_reset import ResetFixture


class AccountBaselineTest(unittest.TestCase):
    def test_current_migrations_and_empty_accounts_sessions_are_required(self):
        temporary = tempfile.TemporaryDirectory(dir='/tmp/opencode')
        self.addCleanup(temporary.cleanup)
        fixture = ResetFixture(Path(temporary.name), chat=True, consolidated=True)
        queries = {
            "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'":
                ','.join(sorted(fixture.tables)),
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank": '\n'.join(fixture.versions),
            **{'SELECT count(*) FROM ' + table: '0' for table in fixture.stores},
            "SELECT count(*) FROM service_writer WHERE singleton AND epoch > 0": "1",
        }
        def check(values):
            with patch.object(e, 'ROOT', fixture.root), \
                    patch.object(e, 'owned', return_value=fixture.resources), \
                    patch.object(e, 'docker', return_value=json.dumps([fixture.image])), \
                    patch.object(e, 'database', side_effect=lambda _, sql: values[sql]), \
                    patch.object(e, 'admin', side_effect=fixture.admin):
                return e.empty_baseline(fixture.value)
        self.assertEqual(check(queries)['migrations'], fixture.versions)
        for sql, bad in [("SELECT count(*) FROM account", "1"), ("SELECT count(*) FROM account_session", "1"),
                         ("SELECT count(*) FROM password_reset_token", "1"), ("SELECT count(*) FROM reset_mail", "1"),
                         ("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", "1"),
                         (next(iter(queries)), "flyway_schema_history,service_writer")]:
            with self.assertRaises(e.Refusal):
                check(dict(queries, **{sql: bad}))
        for table in ('definition', 'account_definition', 'saved_deck', 'deck_entry', 'request_identity', 'account_request'):
            with self.subTest(table=table), self.assertRaises(e.Refusal):
                check(dict(queries, **{'SELECT count(*) FROM ' + table: '1'}))


if __name__ == '__main__':
    unittest.main()
