# 🚀 Carnelia VPN на GitHub

## Если у вас ЕЩЕ НЕТУ аккаунта GitHub:

1. Откройте https://github.com/signup
2. Заполните данные (email, пароль)
3. Подтвердите email
4. Done!

## Если уже есть аккаунт:

### Вариант 1: Создать новый репозиторий (Чистый старт)

1. Откройте https://github.com/new
2. Название: `carnelia-vpn`
3. Описание: `Ultra-light Kotlin/Compose VPN client based on Amnezia`
4. Выберите: **Public** (чтобы все видели)
5. Нажмите **Create repository**

### Вариант 2: Форкнуть и модифицировать

1. Форкните репо Amnezia:
   https://github.com/amnezia-vpn/amnezia-client
2. Переименуйте в Settings → Repository name: `carnelia-vpn`
3. Замените все содержимое нашим кодом

---

## После создания репо на GitHub:

Откройте PowerShell в папке проекта и выполните:

```powershell
cd d:\carneliavpn\carnelia-vpn-fork

# Замените на вашу ссылку репо!
git remote add origin https://github.com/YOUR_USERNAME/carnelia-vpn.git

# Проверьте что добавилось
git remote -v

# Запушьте весь код (master branch)
git branch -M main
git push -u origin main

# Создайте первый релиз (тег)
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

---

## 📍 Ваша ссылка будет:

```
https://github.com/YOUR_USERNAME/carnelia-vpn
```

Замените `YOUR_USERNAME` на ваше имя GitHub.

---

## ✅ После запуша:

1. ✅ Весь код на GitHub
2. ✅ GitHub Actions автоматически соберет APK
3. ✅ APK появится в **Actions** → **Artifacts**
4. ✅ На вкладке **Releases** появится релиз
5. ✅ Все смогут скачать APK

---

**Готовы? Дайте URL репо и я помогу с пушем!**

Пример: `https://github.com/your-username/carnelia-vpn`
