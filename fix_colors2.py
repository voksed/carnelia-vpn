import re

file_path = r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\MainActivity.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace hardcoded White and Gray with MaterialTheme counterparts
content = re.sub(r'Color\.White', 'MaterialTheme.colorScheme.onSurface', content)
content = re.sub(r'Color\.Gray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)', content)
content = re.sub(r'Color\.DarkGray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.4f)', content)
content = re.sub(r'Color\.LightGray', 'MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)', content)

# Remove any double .copy(alpha) issues if they arise
content = re.sub(r'MaterialTheme\.colorScheme\.onSurface\.copy\(alpha=(.*?)\)\.copy\(alpha=(.*?)\)', 'MaterialTheme.colorScheme.onSurface.copy(alpha=\\2)', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("White/Gray Colors replaced in MainActivity.kt")
