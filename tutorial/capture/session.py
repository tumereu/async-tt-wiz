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
