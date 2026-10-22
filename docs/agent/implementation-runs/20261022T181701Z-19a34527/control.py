"""Read-only game observations and one exact owned-backend socket interruption; no business writes."""
import json
import os
from pathlib import Path
import sys
import time

root = Path(__file__).resolve().parents[4]
os.chdir(root)
sys.path.insert(0, str(root))
from e2e import environment as e

prefix = Path(__file__).parent
value = e.load(Path('/tmp/opencode/atw-e2e-19a34527000000000000000000000001'))
operation = sys.argv[1]
with e.locked(Path(value['directory'])):
    owned = e.owned(value)
    backend = owned['backend']
    if operation == 'pause':
        e.docker('pause', backend['Id']); result = dict(paused=True)
    elif operation == 'resume':
        # Closing owned relay channels makes the still-alive app retain its uncertain originals.
        # No extra worker/controller, account API, DB update or replacement game is involved.
        e.stop_relay(value)
        e.docker('unpause', backend['Id'])
        e.wait(value, 90)
        result = dict(resumed=True)
    else:
        assert operation.startswith('observe-')
        expected = {'observe-pending': 'PENDING', 'observe-declined': 'DECLINED', 'observe-reinvited': 'PENDING',
                    'observe-accepted': 'ACCEPTED', 'observe-restarted': 'ACCEPTED'}[operation]
        def observe():
            raw = e.database(value, "SELECT json_build_object('gameId',g.id,'name',g.game_name,'createdAt',g.created_at,'round',g.preparation_round,'invitationId',i.id,'state',i.state,'attempts',(SELECT count(*) FROM invitation),'requests',(SELECT count(*) FROM game_inbox)) FROM game g JOIN invitation i ON i.id=g.current_invitation")
            result = json.loads(raw)
            assert result['state'] == expected
            assert result['name'] == 'Retained Moonlit Rune'
            assert e.database(value, 'SELECT count(*) FROM game') == '1'
            return result
        result = e.deadline(observe, 30)
    (prefix / (operation + '.json')).write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
