import re

file_path = r'c:\Analog-Anchor-Night-Guard\app\src\main\java\com\example\ui\screens\settings\SettingsScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix Arabic Texts
replacements = [
    (r'if \(isEnglish\) "Protected Apps" else "\??[\? \.\:]*\??"', 'if (isEnglish) "Protected Apps" else "التطبيقات المحمية"'),
    (r'if \(isEnglish\) "Choose which apps require the Day Guard mindful gateway." else "\??[\? \.\:]*\??"', 'if (isEnglish) "Choose which apps require the Day Guard mindful gateway." else "اختر التطبيقات التي تتطلب البوابة الواعية لحارس اليوم."'),
    (r'if \(isEnglish\) "Anchor NFC Authority" else "\??[\? \w]*\??"', 'if (isEnglish) "Anchor NFC Authority" else "صلاحية NFC للمرساة"'),
    (r'if \(isEnglish\) "Register the whiteboard Anchor NFC tag. Keep it at your writing station." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Register the whiteboard Anchor NFC tag. Keep it at your writing station." else "قم بتسجيل علامة NFC للمرساة. احتفظ بها في محطة الكتابة الخاصة بك."'),
    (r'if \(isEnglish\) "Whiteboard Anchor NFC tag is registered." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Whiteboard Anchor NFC tag is registered." else "تم تسجيل علامة NFC للمرساة."'),
    (r'if \(isEnglish\) "Policy Edit Window: Active" else "\??[\? \w\:]*\??"', 'if (isEnglish) "Policy Edit Window: Active" else "نافذة تعديل السياسة: نشطة"'),
    (r'if \(isEnglish\) "Policy Edit Window: Locked" else "\??[\? \w\:]*\??"', 'if (isEnglish) "Policy Edit Window: Locked" else "نافذة تعديل السياسة: مقفلة"'),
    (r'if \(isEnglish\) "Session Duration Policy" else "\??[\? \w]*\??"', 'if (isEnglish) "Session Duration Policy" else "سياسة مدة الجلسة"'),
    (r'if \(isEnglish\) "Rules & Triggers" else "\??[\? \w\&]*\??"', 'if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات"'),
    (r'if \(isEnglish\) "Mindful Gateway" else "\??[\? \w]*\??"', 'if (isEnglish) "Mindful Gateway" else "البوابة الواعية"'),
    (r'if \(isEnglish\) "Interrupt every protected app with a mindful pause." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Interrupt every protected app with a mindful pause." else "قاطع كل تطبيق محمي بوقفة واعية."'),
    (r'if \(isEnglish\) "Water Pause" else "\??[\? \w]*\??"', 'if (isEnglish) "Water Pause" else "وقفة الماء"'),
    (r'if \(isEnglish\) "Show a mandatory 30-second deep breath pause before duration choice." else "\??[\? \w\.\-0-9]*\??"', 'if (isEnglish) "Show a mandatory 30-second deep breath pause before duration choice." else "إظهار وقفة تنفس عميق إلزامية لمدة 30 ثانية قبل اختيار المدة."'),
    (r'if \(isEnglish\) "Optional Overlays & Utilities" else "\??[\? \w\&]*\??"', 'if (isEnglish) "Optional Overlays & Utilities" else "تراكبات وأدوات مساعدة اختيارية"'),
    (r'if \(isEnglish\) "Total Usage Pill" else "\??[\? \w]*\??"', 'if (isEnglish) "Total Usage Pill" else "قرص الاستخدام الإجمالي"'),
    (r'if \(isEnglish\) "Show your total screen time in the overlay." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Show your total screen time in the overlay." else "إظهار إجمالي وقت الشاشة في التراكب."'),
    (r'if \(isEnglish\) "Session Usage Overlay" else "\??[\? \w]*\??"', 'if (isEnglish) "Session Usage Overlay" else "تراكب استخدام الجلسة"'),
    (r'if \(isEnglish\) "Show a floating HUD with remaining time when an app is allowed." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Show a floating HUD with remaining time when an app is allowed." else "إظهار شاشة عرض علوية عائمة مع الوقت المتبقي عند السماح بتطبيق."'),
    (r'if \(isEnglish\) "Early Kick" else "\??[\? \w]*\??"', 'if (isEnglish) "Early Kick" else "الطرد المبكر"'),
    (r'if \(isEnglish\) "Build mindfulness by ejecting from apps early." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Build mindfulness by ejecting from apps early." else "بناء الوعي عن طريق الخروج من التطبيقات مبكرًا."'),
    (r'if \(isEnglish\) "Pause Media" else "\??[\? \w]*\??"', 'if (isEnglish) "Pause Media" else "إيقاف الوسائط مؤقتًا"'),
    (r'if \(isEnglish\) "Automatically pause media when an overlay appears." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Automatically pause media when an overlay appears." else "إيقاف الوسائط تلقائيًا عند ظهور التراكب."'),
    (r'if \(isEnglish\) "Whitelist Apps" else "\??[\? \w]*\??"', 'if (isEnglish) "Whitelist Apps" else "تطبيقات القائمة البيضاء"'),
    (r'if \(isEnglish\) "Manage apps that bypass all restrictions" else "\??[\? \w]*\??"', 'if (isEnglish) "Manage apps that bypass all restrictions" else "إدارة التطبيقات التي تتجاوز كل القيود"'),
    (r'if \(isEnglish\) "App Permissions" else "\??[\? \w]*\??"', 'if (isEnglish) "App Permissions" else "أذونات التطبيق"'),
    (r'if \(isEnglish\) "Day Guard" else "\??[\? \w]*\??"', 'if (isEnglish) "Day Guard" else "حارس اليوم"'),
    (r'if \(isEnglish\) "Make access smaller anytime. Making it larger requires the Anchor." else "\??[\? \w\.]*\??"', 'if (isEnglish) "Make access smaller anytime. Making it larger requires the Anchor." else "اجعل الوصول أصغر في أي وقت. تكبيره يتطلب المرساة."'),
    (r'if \(isEnglish\) "General Settings" else "\??[\? \w]*\??"', 'if (isEnglish) "General Settings" else "إعدادات عامة"'),
    (r'if \(isArabic\) "اللغة / Language" else "Language / اللغة"', 'if (!isEnglish) "اللغة / Language" else "Language / اللغة"'),
    (r'if \(isArabic\) "اختر لغة التطبيق المفضلة لديك" else "Select your preferred app language"', 'if (!isEnglish) "اختر لغة التطبيق المفضلة لديك" else "Select your preferred app language"')
]

for pattern, replacement in replacements:
    content = re.sub(pattern, replacement, content)


# 2. Bypass NFC validation
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

# 3. Move Whitelist Apps & Remove General Settings + Language
# We will use string manipulation to remove the General Settings block completely.
whitelist_action_item = """                SettingsActionItem(
                    title = if (isEnglish) "Whitelist Apps" else "تطبيقات القائمة البيضاء",
                    summary = if (isEnglish) "Manage apps that bypass all restrictions" else "إدارة التطبيقات التي تتجاوز كل القيود",
                    onClick = { showWhitelistSheet = true },
                    icon = Icons.Outlined.Checklist,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
"""

# Inject whitelist under Rules & Triggers
rules_triggers_header = 'PreferenceCategory(title = if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات")'
content = content.replace(rules_triggers_header, rules_triggers_header + '\n\n' + whitelist_action_item)

# Remove the General Settings item block
# From '// General Settings Shortcuts' to 'AboutDayGuard'
start_idx = content.find('// General Settings Shortcuts')
end_idx = content.find('item {\n                AboutDayGuard')

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + content[end_idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
