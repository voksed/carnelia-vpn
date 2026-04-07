
import re

path = "D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt"

with open(path, "r", encoding="utf-8") as f:
    text = f.read()

text = re.sub(r"title\s*=\s*\"[^\"]*Coordinates\"", "title = \"?? Координаты\"", text)
text = re.sub(r"title\s*=\s*\"[^\"]*OSM Tile Map & Joystick\"", "title = \"??? OSM Карта & Джойстик\"", text)
text = re.sub(r"title\s*=\s*\"[^\"]*Quick Presets\"", "title = \"?? Быстрые метки\"", text)
text = re.sub(r"title\s*=\s*\"[^\"]*Movement Simulation\"", "title = \"?? Симуляция движения\"", text)

with open(path, "w", encoding="utf-8") as f:
    f.write(text)

