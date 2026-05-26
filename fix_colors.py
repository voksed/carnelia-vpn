import re

file_path = r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\MainActivity.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace hardcoded dark colors with MaterialTheme colorScheme
content = re.sub(r'androidx\.compose\.ui\.graphics\.Color\(0xFF0A0A0A\)', 'MaterialTheme.colorScheme.background', content)
content = re.sub(r'Color\(0xFF0A0A0A\)', 'MaterialTheme.colorScheme.background', content)
content = re.sub(r'Color\(0xFF121212\)', 'MaterialTheme.colorScheme.surface', content)
content = re.sub(r'Color\(0xFF1A1A1A\)', 'MaterialTheme.colorScheme.surface', content)
content = re.sub(r'Color\(0xFF1F1F1F\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF2C2C2C\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF222222\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF333333\)', 'MaterialTheme.colorScheme.outline', content)
content = re.sub(r'Color\(0xFF444444\)', 'MaterialTheme.colorScheme.outline', content)
content = re.sub(r'Color\(0xFF555555\)', 'MaterialTheme.colorScheme.outline', content)

# Replace hardcoded theme-dependent logic with MaterialTheme bindings
# e.g.: if (currentTheme == AppTheme.TON) Color(0xFF0088CC) else Color(0xFFFF1744)
content = re.sub(r'if\s*\(\w+\s*==\s*AppTheme\.TON\)\s*Color\(0xFF[0-9A-Fa-f]{6}\)\s*else\s*Color\(0xFF[0-9A-Fa-f]{6}\)', 'MaterialTheme.colorScheme.primary', content)
content = re.sub(r'if\s*\(\w+\s*==\s*AppTheme\.TON\)\s*listOf\(Color\(0xFF[0-9A-Fa-f]{6}\),\s*Color\(0xFF[0-9A-Fa-f]{6}\)\)\s*else\s*listOf\(Color\(0xFF[0-9A-Fa-f]{6}\),\s*Color\(0xFF[0-9A-Fa-f]{6}\)\)', 'listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)', content)
content = re.sub(r'\(if\s*\(\w+\s*==\s*AppTheme\.TON\)\s*Color\(0xFF[0-9A-Fa-f]{6}\)\s*else\s*Color\(0xFF[0-9A-Fa-f]{6}\)\)', 'MaterialTheme.colorScheme.primaryContainer', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Colors replaced in MainActivity.kt")
