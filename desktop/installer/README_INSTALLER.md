CarneliaVPN Installer
=====================

Требования:
- NSIS (makensis) установлен и доступен в PATH: https://nsis.sourceforge.io/

Как собрать установщик:
1. Убедитесь, что вы собрали проект: `cmake --build build --config Release` или `ninja` в `build`.
2. Перейдите в папку скрипта:

```powershell
cd d:\carneliavpn\carnelia-vpn-desktop\installer
```

3. Запустите сборку установщика:

```powershell
makensis CarneliaVPN.nsi
```

4. Готовый установщик `CarneliaVPN-Installer-2.4.0.exe` появится в текущей папке.

Примечания:
- Скрипт собирает файлы из `..\build`. Если вы хотите включить дополнительные файлы, поместите их в `build` или измените `CarneliaVPN.nsi`.
- Если NSIS отсутствует, можно вместо этого запаковать `build` в ZIP через PowerShell:

```powershell
Compress-Archive -Path ..\build\* -DestinationPath ..\CarneliaVPN-2.4.0.zip -Force
```
