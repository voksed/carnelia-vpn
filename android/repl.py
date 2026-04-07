
import re
with open("D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace("Go to Settings > Developer options > Select mock location app > choose Carnelia VPN", "Перейдите в Настройки > Для разработчиков > Выбрать приложение для фиктивных местоположений > выберите Carnelia VPN")
text = text.replace("Open Developer Settings", "Открыть настройки разработчика")
text = text.replace("?? Coordinates", "?? Координаты")
text = text.replace("\"Latitude\"", "\"Широта\"")
text = text.replace("\"Longitude\"", "\"Долгота\"")
text = text.replace("\"Apply Coordinates\"", "Применить координаты")
text = text.replace("??? OSM Tile Map & Joystick", "??? OSM Карта & Джойстик")
text = text.replace("?? Quick Presets", "?? Быстрые метки")
text = text.replace("?? Movement Simulation", "?? Симуляция движения")
text = text.replace("\"Simulate Movement\"", "\"Симулировать движение\"")
text = text.replace("\"Speed\"", "\"Скорость\"")
text = text.replace("\"Direction (bearing: ${bearing.toInt()}°)\"", "\"Направление (азимут: ${bearing.toInt()}°)\"")

with open("D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt", "w", encoding="utf-8") as f:
    f.write(text)

