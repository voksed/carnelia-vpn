import re

file_path = r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\ui\TrafficMapScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Backgrounds
content = re.sub(r'Color\(0xFF0A0A0A\)', 'MaterialTheme.colorScheme.background', content)
content = re.sub(r'Color\(0xFF111111\)', 'MaterialTheme.colorScheme.surface', content)
content = re.sub(r'Color\(0xFF141414\)', 'MaterialTheme.colorScheme.surface', content)
content = re.sub(r'Color\(0xFF1E1E1E\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF222222\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF333333\)', 'MaterialTheme.colorScheme.outlineVariant', content)

# Foreground / Texts
content = re.sub(r'Color\(0xFF555555\)', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)', content)
content = re.sub(r'Color\(0xFF666666\)', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)', content)
content = re.sub(r'Color\(0xFF888888\)', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)', content)
content = re.sub(r'Color\.White', 'MaterialTheme.colorScheme.onSurface', content)
content = re.sub(r'Color\.Black', 'MaterialTheme.colorScheme.onSurface', content)
content = re.sub(r'Color\.Gray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Colors replaced in TrafficMapScreen.kt")
