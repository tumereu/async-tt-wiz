"""Read synthetic state; suspend only our backend and own debug app around a start acknowledgement."""
from pathlib import Path
import json
import os
import sys
import time

root = Path(__file__).resolve().parents[4]
os.chdir(root)
sys.path.insert(0, str(root))
from e2e import environment as e, devices as d

prefix = Path(__file__).parent
value = e.load(Path('/tmp/opencode/atw-e2e-bc92c3f3000000000000000000000001'))
operation = sys.argv[1]
assert operation in ('observe', 'first-ready', 'withdrawn', 'no-prestart-roll', 'other-ready-preserved', 'pause', 'commit-without-ack', 'resume', 'started', 'final')

def observe():
    return json.loads(e.database(value, """SELECT json_build_object(
      'games',(SELECT coalesce(json_agg(json_build_object('id',id,'name',game_name,'createdAt',created_at,'round',preparation_round,'lifecycle',lifecycle,'play',current_play,'starter',starter_choice::json)), '[]'::json) FROM game),
      'players',(SELECT coalesce(json_agg(json_build_object('id',id,'username',username)), '[]'::json) FROM account),
      'preparations',(SELECT coalesce(json_agg(json_build_object('actor',account_id,'ready',ready,'deck',deck::json)), '[]'::json) FROM player_preparation),
      'plays',(SELECT coalesce(json_agg(payload::json), '[]'::json) FROM game_play),
      'requests',(SELECT coalesce(json_agg(json_build_object('id',request_id,'ordinal',ingress_ordinal,'command',command::json,'result',result::json) ORDER BY ingress_ordinal), '[]'::json) FROM game_inbox))"""))

with e.locked(Path(value['directory'])):
    e.owned(value)
    container = e.compose(value, 'ps', '-q', 'backend').strip()
    assert container
    if operation in ('pause', 'resume'):
        state = json.loads(e.docker('inspect', container))[0]['State']
        if operation == 'pause':
            assert not state['Paused']; e.docker('pause', container)
        elif state['Paused']:
            e.docker('unpause', container)
        print(json.dumps(dict(operation=operation, ok=True)))
    elif operation == 'commit-without-ack':
        device = d.load(Path('/tmp/opencode/atw-device-bc92c3f3000000000000000000000002'))
        d.check_windows(device, d.windows(device, 'snapshot'))
        serial = 'emulator-' + str(device['config']['consolePort'])
        pid = d.adb(device, '-s', serial, 'shell', 'pidof', 'org.asyncttwiz.android').stdout.strip()
        assert pid.isdigit()
        def signal(name):
            d.adb(device, '-s', serial, 'shell', 'run-as', 'org.asyncttwiz.android', 'kill', '-' + name, pid)
        signal('STOP')
        try:
            assert json.loads(e.docker('inspect', container))[0]['State']['Paused']
            e.docker('unpause', container)
            until = time.monotonic() + 30
            while time.monotonic() < until:
                state = observe()
                if len(state['plays']) == 1: break
                time.sleep(.1)
            assert len(state['plays']) == 1
            (prefix / 'committed-before-resume.json').write_text(json.dumps(state, indent=2) + '\n')
            e.restart(value, 'backend', 90)
            e.wait(value, 90)
        finally:
            signal('CONT')
        print(json.dumps(dict(operation=operation, appPid=pid, acceptedPlayBeforeAppResume=True, backendRestarted=True)))
    else:
        state = observe()
        if operation in ('first-ready', 'withdrawn', 'other-ready-preserved'):
            first = next(player['id'] for player in state['players'] if player['username'] == 'Start First')
            assert next(prep['ready'] for prep in state['preparations'] if prep['actor'] == first) == (operation != 'withdrawn')
        if operation in ('first-ready', 'withdrawn', 'no-prestart-roll', 'other-ready-preserved'):
            assert not state['plays'] and state['games'][0]['lifecycle'] == 'LOBBY'
        if operation in ('started', 'final'):
            assert len(state['plays']) == 1 and state['games'][0]['lifecycle'] == 'ACTIVE'
            play = state['plays'][0]
            assert play['gameId'] == state['games'][0]['id'] and play['id'] == state['games'][0]['play']
            assert play['currentNodeId'] == play['root']['id'] and play['board']['turn']['step'] == 'UNTAP'
            assert all(player['life'] == '20' for player in play['board']['players'])
            assert all(not area['cards'] for area in play['board']['containers'] if area['zone'] != 'LIBRARY')
            assert sorted(len(s['order']) for s in play['shuffles']) == [5, 6]
            assert len({s['seed'] for s in play['shuffles']}) == 2
            assert play['starter']['seed'] not in [s['seed'] for s in play['shuffles']]
            assert all(request['result'] is not None for request in state['requests'])
        (prefix / ('observe-' + operation + '-' + str(time.time_ns()) + '.json')).write_text(json.dumps(state, indent=2) + '\n')
        print(json.dumps(state))
