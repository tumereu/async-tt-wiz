"""The account schema must join the existing independent-scenario reset oracle."""
import unittest
from unittest.mock import patch
from e2e import environment as e


class AccountBaselineTest(unittest.TestCase):
    def test_current_migrations_and_empty_accounts_sessions_are_required(self):
        queries = {
            "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'":
                "account,account_session,flyway_schema_history,service_writer",
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank": "1\n2",
            "SELECT count(*) FROM account": "0",
            "SELECT count(*) FROM account_session": "0",
            "SELECT count(*) FROM service_writer WHERE singleton AND epoch > 0": "1",
        }
        state = dict(instance='owned', versions={'prism': 1, 'quilt': 1}, generation=0, messageCount=0,
                     sourceFault='ok', mailFault='ok')
        def check(values):
            with patch.object(e, 'database', side_effect=lambda _, sql: values[sql]), \
                    patch.object(e, 'admin', side_effect=lambda _, path: [] if path == '/admin/messages' else state):
                return e.empty_baseline({'project': 'owned'})
        self.assertEqual(check(queries)['migrations'], ['1', '2'])
        for sql, bad in [("SELECT count(*) FROM account", "1"), ("SELECT count(*) FROM account_session", "1"),
                         ("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", "1"),
                         (next(iter(queries)), "flyway_schema_history,service_writer")]:
            with self.assertRaises(e.Refusal):
                check(dict(queries, **{sql: bad}))


if __name__ == '__main__':
    unittest.main()
