"""Only read synthetic business state or pause/resume this exact owned local backend."""
from pathlib import Path
import json
import os
import sys
root = Path(__file__).resolve().parents[4]
os.chdir(root)
sys.path.insert(0, str(root))
from e2e import environment as e

prefix = Path(__file__).parent
value = e.load(Path('/tmp/opencode/atw-e2e-802a24bd000000000000000000000001'))
operation = sys.argv[1]
assert operation in ('observe', 'partial', 'pause', 'paused', 'resume', 'final')
with e.locked(Path(value['directory'])):
    e.owned(value)
    if operation in ('pause', 'resume'):
        container = e.compose(value, 'ps', '-q', 'backend').strip()
        assert container
        print(json.dumps(dict(operation=operation, result=e.docker('pause' if operation == 'pause' else 'unpause', container))))
    else:
        result = json.loads(e.database(value, "SELECT json_build_object('requests',(SELECT count(*) FROM game_inbox),'rejectedTransfers',(SELECT count(*) FROM game_inbox WHERE command::json->'action'->>'type'='transferPreparedCard' AND result::json->>'outcome'='rejected'),'prepared',(SELECT deck::json FROM player_preparation WHERE deck IS NOT NULL),'sources',(SELECT count(*) FROM saved_deck),'revision',(SELECT max(revision) FROM game_revision))"))
        if operation == 'partial': assert result['rejectedTransfers'] == 1
        (prefix / ('observe-' + operation + '.json')).write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result))
