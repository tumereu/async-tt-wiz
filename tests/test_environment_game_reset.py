"""The real reset oracle must include all implemented ordered-game stores."""
import unittest
from unittest.mock import patch
from e2e import environment as e


class GameResetBaselineTest(unittest.TestCase):
    def test_all_game_stores_are_checked_and_nonempty_one_is_refused(self):
        expected = {'game_inbox', 'game_outbox', 'game_revision', 'game_stream', 'player_preparation', 'game_definition'}
        tables = 'account,account_definition,account_request,account_session,contact,deck_entry,definition,flyway_schema_history,game,game_definition,game_inbox,game_outbox,game_revision,game_stream,invitation,password_reset_token,player_preparation,request_identity,reset_mail,saved_deck,service_writer'
        for dirty in [None, *sorted(expected)]:
            counts = set()
            def database(value, sql):
                if 'string_agg' in sql: return tables
                if 'SELECT version' in sql: return '\n'.join(str(i) for i in range(1, 9))
                if 'service_writer' in sql: return '1'
                table = sql.split(' FROM ')[1]; counts.add(table)
                return '1' if table == dirty else '0'
            def admin(value, path):
                return [] if path == '/admin/messages' else dict(instance='owned', versions={'prism': 1, 'quilt': 1},
                    generation=0, messageCount=0, sourceFault='ok', mailFault='ok')
            with patch.object(e, 'database', database), patch.object(e, 'admin', admin):
                if dirty is not None:
                    with self.assertRaises(e.Refusal): e.empty_baseline({'project': 'owned'})
                else:
                    result = e.empty_baseline({'project': 'owned'})
                    self.assertTrue(expected <= counts)
                    self.assertTrue(expected <= set(result['tables']))
                    self.assertEqual([str(i) for i in range(1, 9)], result['migrations'])


if __name__ == '__main__': unittest.main()
