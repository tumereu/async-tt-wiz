"""The account schema must join the existing independent-scenario reset oracle."""
import unittest
from unittest.mock import patch
from e2e import environment as e


class AccountBaselineTest(unittest.TestCase):
    def test_current_migrations_and_empty_accounts_sessions_are_required(self):
        queries = {
            "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'":
                "account,account_definition,account_request,account_session,deck_entry,definition,flyway_schema_history,password_reset_token,request_identity,reset_mail,saved_deck,service_writer",
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank": "1\n2\n3\n4",
            "SELECT count(*) FROM account": "0",
            "SELECT count(*) FROM account_session": "0",
            "SELECT count(*) FROM password_reset_token": "0",
            "SELECT count(*) FROM reset_mail": "0",
            "SELECT count(*) FROM definition": "0",
            "SELECT count(*) FROM account_definition": "0",
            "SELECT count(*) FROM saved_deck": "0",
            "SELECT count(*) FROM deck_entry": "0",
            "SELECT count(*) FROM request_identity": "0",
            "SELECT count(*) FROM account_request": "0",
            "SELECT count(*) FROM service_writer WHERE singleton AND epoch > 0": "1",
        }
        state = dict(instance='owned', versions={'prism': 1, 'quilt': 1}, generation=0, messageCount=0,
                     sourceFault='ok', mailFault='ok')
        def check(values):
            with patch.object(e, 'database', side_effect=lambda _, sql: values[sql]), \
                    patch.object(e, 'admin', side_effect=lambda _, path: [] if path == '/admin/messages' else state):
                return e.empty_baseline({'project': 'owned'})
        self.assertEqual(check(queries)['migrations'], ['1', '2', '3', '4'])
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
