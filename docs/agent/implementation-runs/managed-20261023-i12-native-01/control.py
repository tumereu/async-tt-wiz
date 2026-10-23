"""Only observe synthetic SQL or interrupt this driver's exact backend/app transport. No state injection."""
from pathlib import Path
import json
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))
from e2e import environment as e, devices as d

PREFIX = Path(__file__).parent
active = json.loads((PREFIX / 'active-environment.json').read_text())
value = e.load(Path(active['environment']))
operation = sys.argv[1]
assert operation in ('observe', 'pause', 'resume', 'recover-original', 'original-evidence', 'disconnect-first', 'reconnect-first')

def observe():
    state = json.loads(e.database(value, """SELECT json_build_object(
      'games',(SELECT coalesce(json_agg(json_build_object('id',id,'name',game_name,'createdAt',created_at,'round',preparation_round,'lifecycle',lifecycle,'play',current_play,'invitation',current_invitation,'starter',starter_choice::json) ORDER BY id), '[]'::json) FROM game),
      'players',(SELECT coalesce(json_agg(json_build_object('id',id,'username',username) ORDER BY id), '[]'::json) FROM account),
      'preparations',(SELECT coalesce(json_agg(json_build_object('game',game_id,'actor',account_id,'ready',ready,'deck',deck::json) ORDER BY game_id,account_id), '[]'::json) FROM player_preparation),
      'definitions',(SELECT coalesce(json_agg(json_build_object('game',g.game_id,'digest',g.digest,'payload',d.payload::json) ORDER BY g.game_id,g.digest), '[]'::json) FROM game_definition g JOIN definition d USING(digest)),
      'sources',(SELECT coalesce(json_agg(json_build_object('id',id,'name',name,'revision',revision) ORDER BY id), '[]'::json) FROM saved_deck),
      'plays',(SELECT coalesce(json_agg(payload::json ORDER BY id), '[]'::json) FROM game_play),
      'streams',(SELECT coalesce(json_agg(json_build_object('game',game_id,'actor',account_id,'process',process_id,'stream',stream_id,'generation',generation) ORDER BY game_id,account_id,installation_id), '[]'::json) FROM game_stream),
      'requests',(SELECT coalesce(json_agg(json_build_object('id',request_id,'game',game_id,'ordinal',ingress_ordinal,'command',command::json,'result',result::json) ORDER BY game_id,ingress_ordinal), '[]'::json) FROM game_inbox))"""))
    # Public UUIDv5 role/index contract, independently expanded from observed prepared ranges.
    state['preparedCards'] = [dict(game=p['game'], owner=p['actor'], definition=entry['definition'], section=entry['section'],
        id=str(uuid.uuid5(uuid.UUID(p['deck']['selectionRequestId']), 'prepared-card/' + str(int(entry['firstIndex']) + offset))))
        for p in state['preparations'] if p['deck'] for entry in p['deck']['entries'] for offset in range(entry['quantity'])]
    return state

with e.locked(Path(value['directory'])):
    e.owned(value)
    container = e.compose(value, 'ps', '-a', '-q', 'backend').strip()
    assert container
    if operation == 'observe':
        result = observe()
        (PREFIX / ('observe-' + str(time.time_ns()) + '.json')).write_text(json.dumps(result, indent=2) + '\n')
    elif operation == 'original-evidence':
        result = json.loads((PREFIX / 'original-before-app-resume.json').read_text())
    elif operation in ('pause', 'resume'):
        state = json.loads(e.docker('inspect', container))[0]['State']
        if operation == 'pause':
            assert not state['Paused']; e.docker('pause', container)
        elif state['Paused']: e.docker('unpause', container)
        result = dict(operation=operation, container=container)
    elif operation in ('disconnect-first', 'reconnect-first'):
        if operation == 'disconnect-first': e.docker('stop', '--time', '30', container)
        else: e.restart(value, 'backend', 90); e.wait(value, 90)
        result = dict(operation=operation, container=container)
    else:
        device = d.load(Path(active['devices'][0]))
        d.check_windows(device, d.windows(device, 'snapshot'))
        serial = 'emulator-' + str(device['config']['consolePort'])
        pid = d.adb(device, '-s', serial, 'shell', 'pidof', 'org.asyncttwiz.android').stdout.strip()
        assert pid.isdigit()
        def signal(name):
            d.adb(device, '-s', serial, 'shell', 'run-as', 'org.asyncttwiz.android', 'kill', '-' + name, pid)
        signal('STOP')
        try:
            assert json.loads(e.docker('inspect', container))[0]['State']['Paused']
            # Close the exact owned public relay BEFORE execution: an acknowledgement
            # cannot sit unread in the suspended client's socket and masquerade as recovery.
            e.stop_relay(value)
            e.docker('unpause', container)
            until = time.monotonic() + 45
            while True:
                state = observe()
                game = next(g for g in state['games'] if g['name'] == 'Native Pending Cancel')
                originals = [r for r in state['requests'] if r['game'] == game['id'] and r['command']['action']['type'] == 'cancelSetup']
                if game['lifecycle'] == 'CANCELLED' and len(originals) == 1 and originals[0]['result']: break
                assert time.monotonic() < until, 'No original accepted cancellation before client resumes'
                time.sleep(.1)
            result = dict(original=originals[0], streams=state['streams'], appPid=pid, sameProcessRetained=True,
                ownedRelayClosedBeforeAcceptance=True)
            (PREFIX / 'original-before-app-resume.json').write_text(json.dumps(result, indent=2) + '\n')
            e.restart(value, 'backend', 90); e.wait(value, 90)
        finally: signal('CONT')
    print(json.dumps(result))
