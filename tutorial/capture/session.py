"""Shared helpers for interactive capture sessions."""
import sys, time
sys.path.insert(0, __import__('os').path.dirname(__file__))
from drive import Device

CLIPS = __import__('os').path.join(__import__('os').path.dirname(__file__), '..', 'video', 'public', 'clips')
SHOTS = __import__('os').path.join(__import__('os').path.dirname(__file__), '..', 'video', 'public', 'shots')
a = Device('emulator-5554')
b = Device('emulator-5556')

def show(d, name):
    small = d.look(f"raw/s/{name}.png")
    for n in d.nodes():
        if n['text'] or n['desc']:
            print((n['text'] or n['desc'])[:70].replace('\n', ' '), n['center'])
    print(small)

def set_value(d, field_xy, value, save_label='Save'):
    d.tap(*field_xy); time.sleep(0.4)
    d.key(123)
    for _ in range(6):
        d.key(67)
    d.text(value); time.sleep(0.3)
    d.tap_label(save_label, exact=True); time.sleep(1)

def menu_action(d, card_xy, label, times=1):
    """Open a card's menu, go to the page holding `label`, press it `times`, close the menu."""
    d.tap(*card_xy); time.sleep(1.3)
    for _ in range(4):
        hit = [n for n in d.nodes() if n['desc'] == label]
        if hit:
            break
        nxt = [n for n in d.nodes() if n['desc'] == 'Next page'][0]
        d.tap(*nxt['center']); time.sleep(1.0)
    for _ in range(times):
        d.tap(*hit[0]['center']); time.sleep(0.8)
