#!/usr/bin/env python3
"""Превращает v2rayNG в приложение «одна кнопка» с вшитым профилем.

Запуск:  VPN_LINK='vless://...' python3 apply_patch.py <путь к клону v2rayNG> <путь к overlay>
"""
import os
import re
import shutil
import sys

src, overlay = sys.argv[1], sys.argv[2]
variant = os.environ.get("BUILD_VARIANT", "family").strip() or "family"
if variant == "public":
    link = ""  # публичная версия: ключа внутри нет, пользователь вставляет ссылку доступа
else:
    raw_link = os.environ.get("VPN_LINK", "")
    # Берём первую ссылку vless:// (в секрет можно вставить весь файл vpn-links.txt целиком).
    found = re.search(r"vless://\S+", raw_link)
    link = found.group(0) if found else raw_link.strip()
    profile_file = os.path.join(overlay, "profile.txt")
    if not link and os.path.exists(profile_file):
        with open(profile_file, encoding="utf-8") as f:
            link = f.read().strip()
    if not link:
        print("ВНИМАНИЕ: ссылка не задана, собираю ТЕСТОВУЮ сборку с заглушкой (VPN не заработает).")
        link = "vless://00000000-0000-0000-0000-000000000000@127.0.0.1:443?encryption=none&security=none&type=tcp#placeholder"

app = os.path.join(src, "V2rayNG", "app")
kt_dir = os.path.join(app, "src/main/java/com/v2ray/ang/ui/main")


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


# 1. Экран с одной кнопкой
shutil.copy(os.path.join(overlay, "OneButtonScreen.kt"), os.path.join(kt_dir, "OneButtonScreen.kt"))

# 2. Вшитый профиль (ссылка берётся из секрета и не лежит в репозитории)
escaped = link.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
write(
    os.path.join(kt_dir, "BundledProfile.kt"),
    'package com.v2ray.ang.ui.main\n\nobject BundledProfile {\n    const val LINK = "%s"\n}\n' % escaped,
)

# 3. MainActivity
path = os.path.join(kt_dir, "MainActivity.kt")
s = read(path)

anchor = "        mainViewModel.onAction(MainAction.Initialize)\n"
if anchor not in s:
    sys.exit("MainActivity: не найден onCreate-якорь, исходник v2rayNG изменился.")
s = s.replace(anchor, anchor + "        ensureBundledProfile()\n", 1)

pattern = re.compile(
    r"(override fun ScreenContent\(\) \{)(.*?)(\n    \}\n\n    private fun shareToClipboard)", re.S
)
if not pattern.search(s):
    sys.exit("MainActivity: не найден ScreenContent, исходник v2rayNG изменился.")
new_body = """
        BackHandler { moveTaskToBack(false) }
        OneButtonScreen(
            mainViewModel = mainViewModel,
            onToggle = { handleFabAction() },
            onImportLink = { importAccessLink(it) },
        )"""
s = pattern.sub(lambda m: m.group(1) + new_body + m.group(3), s, count=1)

helper = '''    private fun ensureBundledProfile() {
        val link = BundledProfile.LINK
        if (link.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            if (MmkvManager.decodeAllServerList().isEmpty()) {
                AngConfigManager.importBatchConfig(link, "", false)
            }
            val all = MmkvManager.decodeAllServerList()
            val selected = MmkvManager.getSelectServer()
            withContext(Dispatchers.Main) {
                if (all.isNotEmpty() && (selected.isNullOrEmpty() || !all.contains(selected))) {
                    mainViewModel.updateSelectedGuid(all.first())
                }
                mainViewModel.onAction(MainAction.RefreshGroups)
            }
        }
    }

'''
helper = helper + '\n    private fun importAccessLink(text: String) {\n        val link = Regex("vless://\\\\S+").find(text)?.value\n        if (link == null) {\n            android.widget.Toast.makeText(this, "Вставьте ссылку, которая начинается с vless://", android.widget.Toast.LENGTH_LONG).show()\n            return\n        }\n        lifecycleScope.launch(Dispatchers.IO) {\n            AngConfigManager.importBatchConfig(link, "", true)\n            val all = MmkvManager.decodeAllServerList()\n            withContext(Dispatchers.Main) {\n                if (all.isNotEmpty()) mainViewModel.updateSelectedGuid(all.last())\n                mainViewModel.onAction(MainAction.RefreshGroups)\n            }\n        }\n    }\n\n'
marker = "    private fun shareToClipboard"
s = s.replace(marker, helper + marker, 1)
write(path, s)

# 4. Название и идентификатор приложения
app_name = "VPN Вьетнам" if variant == "public" else "VPN Семья"
res_dir = os.path.join(app, "src/main/res")
for d in os.listdir(res_dir):
    sp = os.path.join(res_dir, d, "strings.xml")
    if d.startswith("values") and os.path.isfile(sp):
        t = read(sp)
        t2 = re.sub(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', lambda m: m.group(1) + app_name + m.group(2), t)
        if t2 != t:
            write(sp, t2)

# Значок: флаг Вьетнама
icons = os.path.join(overlay, "res")
if os.path.isdir(icons):
    shutil.copytree(icons, res_dir, dirs_exist_ok=True)

gradle = os.path.join(app, "build.gradle.kts")
g = read(gradle)
if 'applicationId = "com.v2ray.ang"' not in g:
    sys.exit("build.gradle.kts: не найден applicationId.")
g = g.replace('applicationId = "com.v2ray.ang"', 'applicationId = "com.family.vpn"', 1)
write(gradle, g)

print("Патч применён.")
