
import re

path = "D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt"

with open(path, "r", encoding="utf-8") as f:
    text = f.read()

# Fix translations
text = text.replace("Go to Settings > Developer options > Select mock location app > choose Carnelia VPN", "Перейдите в Настройки > Для разработчиков > Выбрать приложение для фикт. местоположений > выберите Carnelia VPN")
text = text.replace("Open Developer Settings", "Открыть настройки разработчика")

# Using regex to bypass exact emoji matches, just replacing the title pattern
text = re.sub(r"title = \".*?Coordinates\"", "title = \"?? Координаты\"", text)
text = text.replace("\"Latitude\"", "\"Широта\"")
text = text.replace("\"Longitude\"", "\"Долгота\"")
text = text.replace("\"Apply Coordinates\"", "\"Применить координаты\"")
text = re.sub(r"title = \".*?OSM Tile Map & Joystick\"", "title = \"??? OSM Карта и Джойстик\"", text)
text = re.sub(r"title = \".*?Quick Presets\"", "title = \"?? Быстрые метки\"", text)
text = re.sub(r"title = \".*?Movement Simulation\"", "title = \"?? Симуляция движения\"", text)
text = text.replace("\"Simulate Movement\"", "\"Симулировать движение\"")
text = text.replace("\"Speed\"", "\"Скорость\"")
text = re.sub(r"\"Direction \(bearing.*?\)\"", "\"Направление (азимут: ${bearing.toInt()}°)\"", text)
text = text.replace("if (isRunning) \"Stop\" else \"Start\"", "if (isRunning) \"Остановить\" else \"Запустить\"")

# Add text color to OutlinedTextField to fix dark/light mode issues
if "focusedTextColor =" not in text:
    text = text.replace(
        "focusedBorderColor = MaterialTheme.colorScheme.primary,",
        "focusedBorderColor = MaterialTheme.colorScheme.primary,\n                            focusedTextColor = MaterialTheme.colorScheme.onSurface,\n                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,"
    )

with open(path, "w", encoding="utf-8") as f:
    f.write(text)

