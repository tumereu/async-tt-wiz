"""Convert the app's vector drawable logo to SVG: logo.py <atw_logo.xml> <out.svg>"""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()
w, h = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
paths = "".join(f'<path fill="{p.get(A + "fillColor")}" d="{p.get(A + "pathData")}"/>' for p in root.iter("path"))
open(sys.argv[2], "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">{paths}</svg>')
