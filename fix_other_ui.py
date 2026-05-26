import re
import os

files = [
    r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\ui\DAppBrowserScreen.kt",
    r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\ui\ManualEntryDialog.kt"
]

for file_path in files:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Backgrounds
    content = re.sub(r'Color\(0xFF0A0A0A\)', 'MaterialTheme.colorScheme.background', content)
    content = re.sub(r'Color\(0xFF141414\)', 'MaterialTheme.colorScheme.surface', content)
    content = re.sub(r'Color\(0xFF1E1E1E\)', 'MaterialTheme.colorScheme.surfaceVariant', content)
    content = re.sub(r'Color\(0xFF333333\)', 'MaterialTheme.colorScheme.outlineVariant', content)
    
    # Texts
    content = re.sub(r'Color\.White', 'MaterialTheme.colorScheme.onSurface', content)
    content = re.sub(r'Color\.Black', 'MaterialTheme.colorScheme.onSurface', content)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Colors replaced in other UI files.")
