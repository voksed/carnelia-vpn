import re

file_path = r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\SettingsActivity.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace hardcoded dark colors
content = re.sub(r'Color\(0xFF1E1E1E\)', 'MaterialTheme.colorScheme.surface', content)
content = re.sub(r'Color\(0xFF222222\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
content = re.sub(r'Color\(0xFF2C2C2C\)', 'MaterialTheme.colorScheme.outlineVariant', content)
content = re.sub(r'Color\(0xFF333333\)', 'MaterialTheme.colorScheme.surfaceVariant', content)

# Fix text colors
content = re.sub(r'Color\.LightGray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)', content)
content = re.sub(r'Color\.Gray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)', content)
content = re.sub(r'Color\.White', 'MaterialTheme.colorScheme.onSurface', content)
content = re.sub(r'Color\.Black', 'MaterialTheme.colorScheme.onSurface', content)

# Clean double alphas
content = re.sub(r'MaterialTheme\.colorScheme\.onSurface\.copy\(alpha=(.*?)\)\.copy\(alpha=(.*?)\)', 'MaterialTheme.colorScheme.onSurface.copy(alpha=\\2)', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Colors replaced in SettingsActivity.kt")
