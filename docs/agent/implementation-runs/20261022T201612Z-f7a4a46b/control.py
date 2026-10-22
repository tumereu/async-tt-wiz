"""Read-only assertions on this unit's exact owned synthetic preparation; no business writes."""
import json
import os
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[4]
os.chdir(root)
sys.path.insert(0, str(root))
from e2e import environment as e

prefix = Path(__file__).parent
value = e.load(Path('/tmp/opencode/atw-e2e-f7a4a46b000000000000000000000001'))
operation = sys.argv[1]
assert operation in ('draft', 'selected', 'restarted', 'edited', 'deleted', 'final', 'accepted')
with e.locked(Path(value['directory'])):
    e.owned(value)
    def observe():
        result = json.loads(e.database(value, "SELECT json_build_object('gameId',id,'name',game_name,'createdAt',created_at,'round',preparation_round,'requests',(SELECT count(*) FROM game_inbox),'prepared',(SELECT count(*) FROM player_preparation WHERE deck IS NOT NULL),'sources',(SELECT count(*) FROM saved_deck),'copy',(SELECT deck::json FROM player_preparation WHERE deck IS NOT NULL),'definitions',(SELECT count(*) FROM game_definition)) FROM game"))
        assert result['name'] == 'Native Prepared Spark'
        assert result['requests'] == (0 if operation == 'draft' else 2 if operation == 'accepted' else 1)
        assert result['prepared'] == (0 if operation == 'draft' else 1)
        if operation != 'draft':
            assert result['copy']['name'] == 'Picker source'
            assert result['definitions'] == 3
        if operation in ('deleted', 'final'): assert result['sources'] == 1
        if operation == 'accepted':
            assert e.database(value, "SELECT state FROM invitation WHERE id=(SELECT current_invitation FROM game)") == 'ACCEPTED'
            assert result['copy'] == json.loads((prefix / 'observe-final.json').read_text())['copy']
        return result
    result = e.deadline(observe, 30)
(prefix / ('observe-' + operation + '.json')).write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
