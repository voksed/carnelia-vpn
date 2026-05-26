import re

file_path = r"D:\carneliavpn\carnelia-vpn-fork\android\app\src\main\kotlin\com\carnelia\vpn\ui\TrafficMapScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace MaterialTheme inside drawTrafficMap with hardcoded colors again, to avoid compile error.
# since the traffic map is basically an autonomous graphic component, light/dark mode for the canvas lines isn't that necessary, and doing it properly requires passing 5-6 parameters down.

# Find the start of drawTrafficMap
start_idx = content.find("private fun DrawScope.drawTrafficMap(")

if start_idx != -1:
    end_idx = content.find("internal fun PacketTraceContent(context: Context) {")
    if end_idx == -1: end_idx = len(content)
    
    # Extract just the function body
    func_body = content[start_idx:end_idx]
    
    # Revert MaterialTheme colors in the function body
    func_body = re.sub(r'MaterialTheme\.colorScheme\.onSurface\.copy\(alpha=0\.6f\)', 'Color(0xFF888888)', func_body)
    func_body = re.sub(r'MaterialTheme\.colorScheme\.onSurface', 'Color.White', func_body)
    func_body = re.sub(r'MaterialTheme\.colorScheme\.background', 'Color(0xFF0A0A0A)', func_body)
    func_body = re.sub(r'MaterialTheme\.colorScheme\.outlineVariant', 'Color(0xFF333333)', func_body)
    func_body = re.sub(r'MaterialTheme\.colorScheme\.surfaceVariant', 'Color(0xFF1E1E1E)', func_body)
    func_body = re.sub(r'MaterialTheme\.colorScheme\.surface', 'Color(0xFF141414)', func_body)

    # Put back
    content = content[:start_idx] + func_body + content[end_idx:]

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Canvas colors reverted")
