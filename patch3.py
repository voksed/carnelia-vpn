import re
f = open('android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt', 'r', encoding='utf-8')
c = f.read()
f.close()
c = c.replace('"🌍 Quick Presets"', '"🌍 Быстрые пресеты"')
c = c.replace('"🚶 Movement Simulation"', '"🚶 Симуляция движения"')
c = c.replace('"Select a predefined location:"', '"Выберите заранее заданную локацию:"')
c = c.replace('"Set Marker"', '"Установить метку"')
c = c.replace('"Latitude"', '"Широта"')
c = c.replace('"Longitude"', '"Долгота"')
c = c.replace('"Speed (m/s)"', '"Скорость (м/с)"')
c = c.replace('"Start Direction"', '"Направление начала"')
c = c.replace('"Stop"', '"Стоп"')
c = c.replace('"Start"', '"Старт"')
c = c.replace('"Joystick"', '"Джойстик"')
c = c.replace('"Refresh Map"', '"Обновить карту"')
c = c.replace('"Simulating movement..."', '"Симуляция движения..."')
c = c.replace('"Simulating"', '"Симуляция"')
c = c.replace('"Go to Settings"', '"Перейти в Настройки"')
c = c.replace('"Active"', '"Активно"')
c = c.replace('"Not Active"', '"Не активно"')
c = c.replace('"Spoofing is OFF"', '"Спуфинг ВЫКЛЮЧЕН"')
c = c.replace('"Geo Spoof is ACTIVE"', '"Гео-спуфинг АКТИВЕН"')
c = c.replace('"Warning: Developer Options/Mock locations not enabled!"', '"Внимание: Режим разработчика/Фиктивные местоположения не включены!"')
c = c.replace('"Open Settings"', '"Открыть настройки"')

# Also fix the colors text for Speed and direction
c = re.sub(
    r'label = \{ Text\("Скорость \(м/с\)"\) \}',
    r'label = { Text("Скорость (м/с)", color = MaterialTheme.colorScheme.onSurface) }',
    c
)

c = re.sub(
    r'label = \{ Text\("Направление начала"\) \}',
    r'label = { Text("Направление начала", color = MaterialTheme.colorScheme.onSurface) }',
    c
)

# And OutlinedTextField colors for speed/dir just to be safe
c = re.sub(
    r'(OutlinedTextField\([\s\S]*?label = \{ Text\("Скорость[\s\S]*?)(modifier =)',
    r'\1colors = OutlinedTextFieldDefaults.colors(\n                    focusedTextColor = MaterialTheme.colorScheme.onSurface,\n                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface\n                ),\n                \2',
    c
)

f = open('android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt', 'w', encoding='utf-8')
f.write(c)
f.close()
