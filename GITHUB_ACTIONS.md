# GitHub Actions - Инструкция по автосборке APK

## Шаг 1: Создай репозиторий на GitHub

1. Открой https://github.com/new
2. Название: `carnelia-vpn` (или любое)
3. Публичный/Приватный — на твой выбор
4. **НЕ** добавляй README/LICENSE (уже есть локально)
5. Нажми **Create repository**

## Шаг 2: Пуш в GitHub

Скопируй URL твоего репо (например: `https://github.com/yourusername/carnelia-vpn.git`), затем:

```bash
cd d:\carneliavpn\carnelia-vpn
git remote add origin https://github.com/ТВОЙ_USERNAME/carnelia-vpn.git
git branch -M main
git push -u origin main
```

## Шаг 3: GitHub Actions автоматически соберёт APK

После push в GitHub:
- Открой репо → вкладка **Actions**
- Увидишь запущенный workflow "Android Build CI"
- Через 5-10 минут сборка завершится
- APK будут в **Artifacts** (скачай ZIP с APK)

## Шаг 4: Скачать APK

### Вариант A: Artifacts (всегда)
1. Открой конкретный запуск workflow
2. Внизу секция **Artifacts**
3. Скачай `carnelia-vpn-debug` или `carnelia-vpn-release`

### Вариант B: Releases (для тегов)
1. Создай тег локально и пушни:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. Открой репо → **Releases**
3. APK будет в релизе автоматически

## Установка на телефон

```bash
adb install carnelia-vpn-release.apk
```

---

**Готово!** Теперь каждый push в GitHub = автоматическая сборка APK в облаке. 🚀
