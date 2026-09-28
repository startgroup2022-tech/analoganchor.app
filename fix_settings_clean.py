import re

file_path = r'c:\Analog-Anchor-Night-Guard\app\src\main\java\com\example\ui\screens\settings\SettingsScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add isEnglish to function signatures
content = content.replace('fun SettingsScreen(', 'fun SettingsScreen(\n    isEnglish: Boolean,')
content = content.replace('NfcAuthoritySection(preferences = preferences)', 'NfcAuthoritySection(preferences = preferences, isEnglish = isEnglish)')
content = content.replace('SessionDurationChooser(\n                    currentPolicy = preferences.sessionDurationPolicy,', 'SessionDurationChooser(\n                    isEnglish = isEnglish,\n                    currentPolicy = preferences.sessionDurationPolicy,')
content = content.replace('AboutDayGuard(onOpenPermissions = onOpenPermissions)', 'AboutDayGuard(isEnglish = isEnglish, onOpenPermissions = onOpenPermissions)')

content = content.replace('fun NfcAuthoritySection(preferences: UserPreferences) {', 'fun NfcAuthoritySection(preferences: UserPreferences, isEnglish: Boolean) {')
content = content.replace('fun SessionDurationChooser(\n    currentPolicy: Int,', 'fun SessionDurationChooser(\n    isEnglish: Boolean,\n    currentPolicy: Int,')
content = content.replace('fun AboutDayGuard(onOpenPermissions: () -> Unit) {', 'fun AboutDayGuard(isEnglish: Boolean, onOpenPermissions: () -> Unit) {')

# 2. String translations
replacements = {
    '"Protected Apps"': 'if (isEnglish) "Protected Apps" else "التطبيقات المحمية"',
    '"Choose which apps require the Day Guard mindful gateway."': 'if (isEnglish) "Choose which apps require the Day Guard mindful gateway." else "اختر التطبيقات التي تتطلب البوابة الواعية لحارس اليوم."',
    '"Anchor NFC Authority"': 'if (isEnglish) "Anchor NFC Authority" else "صلاحية NFC للمرساة"',
    '"Register the whiteboard Anchor NFC tag. Keep it at your writing station."': 'if (isEnglish) "Register the whiteboard Anchor NFC tag. Keep it at your writing station." else "قم بتسجيل علامة NFC للمرساة. احتفظ بها في محطة الكتابة الخاصة بك."',
    '"Whiteboard Anchor NFC tag is registered."': 'if (isEnglish) "Whiteboard Anchor NFC tag is registered." else "تم تسجيل علامة NFC للمرساة."',
    '"Policy Edit Window: Active"': 'if (isEnglish) "Policy Edit Window: Active" else "نافذة تعديل السياسة: نشطة"',
    '"Policy Edit Window: Locked"': 'if (isEnglish) "Policy Edit Window: Locked" else "نافذة تعديل السياسة: مقفلة"',
    '"Session Duration Policy"': 'if (isEnglish) "Session Duration Policy" else "سياسة مدة الجلسة"',
    '"Rules & Triggers"': 'if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات"',
    '"Mindful Gateway"': 'if (isEnglish) "Mindful Gateway" else "البوابة الواعية"',
    '"Interrupt every protected app with a mindful pause."': 'if (isEnglish) "Interrupt every protected app with a mindful pause." else "قاطع كل تطبيق محمي بوقفة واعية."',
    '"Water Pause"': 'if (isEnglish) "Water Pause" else "وقفة الماء"',
    '"Show a mandatory 30-second deep breath pause before duration choice."': 'if (isEnglish) "Show a mandatory 30-second deep breath pause before duration choice." else "إظهار وقفة تنفس عميق إلزامية لمدة 30 ثانية قبل اختيار المدة."',
    '"Optional Overlays & Utilities"': 'if (isEnglish) "Optional Overlays & Utilities" else "تراكبات وأدوات مساعدة اختيارية"',
    '"Total Usage Pill"': 'if (isEnglish) "Total Usage Pill" else "قرص الاستخدام الإجمالي"',
    '"Show your total screen time in the overlay."': 'if (isEnglish) "Show your total screen time in the overlay." else "إظهار إجمالي وقت الشاشة في التراكب."',
    '"Session Usage Overlay"': 'if (isEnglish) "Session Usage Overlay" else "تراكب استخدام الجلسة"',
    '"Show a floating HUD with remaining time when an app is allowed."': 'if (isEnglish) "Show a floating HUD with remaining time when an app is allowed." else "إظهار شاشة عرض علوية عائمة مع الوقت المتبقي عند السماح بتطبيق."',
    '"Early Kick"': 'if (isEnglish) "Early Kick" else "الطرد المبكر"',
    '"Build mindfulness by ejecting from apps early."': 'if (isEnglish) "Build mindfulness by ejecting from apps early." else "بناء الوعي عن طريق الخروج من التطبيقات مبكرًا."',
    '"Pause Media"': 'if (isEnglish) "Pause Media" else "إيقاف الوسائط مؤقتًا"',
    '"Automatically pause media when an overlay appears."': 'if (isEnglish) "Automatically pause media when an overlay appears." else "إيقاف الوسائط تلقائيًا عند ظهور التراكب."',
    '"Whitelist Apps"': 'if (isEnglish) "Whitelist Apps" else "تطبيقات القائمة البيضاء"',
    '"Manage apps that bypass all restrictions"': 'if (isEnglish) "Manage apps that bypass all restrictions" else "إدارة التطبيقات التي تتجاوز كل القيود"',
    '"App Permissions"': 'if (isEnglish) "App Permissions" else "أذونات التطبيق"',
    '"Day Guard"': 'if (isEnglish) "Day Guard" else "حارس اليوم"',
    '"Make access smaller anytime. Making it larger requires the Anchor."': 'if (isEnglish) "Make access smaller anytime. Making it larger requires the Anchor." else "اجعل الوصول أصغر في أي وقت. تكبيره يتطلب المرساة."'
}

for eng, translated in replacements.items():
    content = content.replace(eng, translated)

# 3. Bypass NFC logic
content = re.sub(
    r'if \(!checked\) \{\s*if \(isEditWindowActive\) \{\s*coroutineScope\.launch \{\s*preferencesRepository\.setMindfulGatewayEnabled\(false\)\s*\}\s*\} else \{\s*Toast\.makeText\(context, context\.getString\(com\.example\.R\.string\.return_to_nfc\), Toast\.LENGTH_LONG\)\.show\(\)\s*\}\s*\} else \{\s*coroutineScope\.launch \{\s*preferencesRepository\.setMindfulGatewayEnabled\(true\)\s*\}\s*\}',
    r'coroutineScope.launch { preferencesRepository.setMindfulGatewayEnabled(checked) }',
    content
)

content = re.sub(
    r'if \(!checked\) \{\s*if \(isEditWindowActive\) \{\s*coroutineScope\.launch \{\s*preferencesRepository\.setWaterPauseEnabled\(false\)\s*\}\s*\} else \{\s*Toast\.makeText\(context, context\.getString\(com\.example\.R\.string\.return_to_nfc\), Toast\.LENGTH_LONG\)\.show\(\)\s*\}\s*\} else \{\s*coroutineScope\.launch \{\s*preferencesRepository\.setWaterPauseEnabled\(true\)\s*\}\s*\}',
    r'coroutineScope.launch { preferencesRepository.setWaterPauseEnabled(checked) }',
    content
)

content = re.sub(
    r'if \(minutes < currentPolicy\) \{\s*onPolicyChange\(minutes\)\s*\} else if \(isEditWindowActive\) \{\s*onPolicyChange\(minutes\)\s*\} else \{\s*Toast\.makeText\(context, context\.getString\(com\.example\.R\.string\.return_to_nfc\), Toast\.LENGTH_LONG\)\.show\(\)\s*\}',
    r'onPolicyChange(minutes)',
    content
)

# 4. Remove General Settings entirely & Move Whitelist Apps
whitelist_block = """                SettingsActionItem(
                    title = if (isEnglish) "Whitelist Apps" else "تطبيقات القائمة البيضاء",
                    summary = if (isEnglish) "Manage apps that bypass all restrictions" else "إدارة التطبيقات التي تتجاوز كل القيود",
                    onClick = { showWhitelistSheet = true },
                    icon = Icons.Outlined.Checklist,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
"""

content = content.replace('PreferenceCategory(title = if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات")', 'PreferenceCategory(title = if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات")\n' + whitelist_block)

# Delete from 'PreferenceCategory(title = "General Settings")' to just before 'item {\n                AboutDayGuard'
start_idx = content.find('            // General Settings Shortcuts')
end_idx = content.find('            item {\n                AboutDayGuard')

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + content[end_idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
