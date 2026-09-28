import re

file_path = r'c:\Analog-Anchor-Night-Guard\app\src\main\java\com\example\ui\screens\settings\SettingsScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix SessionDurationChooser invocation
content = content.replace('SessionDurationChooser(\n                    currentPolicy = preferences.allowedDurationPolicyMinutes,', 'SessionDurationChooser(\n                    isEnglish = isEnglish,\n                    currentPolicy = preferences.allowedDurationPolicyMinutes,')

# String Resource replacements
replacements = {
    'stringResource(R.string.protected_apps)': 'if (isEnglish) "Protected Apps" else "التطبيقات المحمية"',
    'stringResource(R.string.choose_which_apps_require_the_day_guard_mindful_gateway)': 'if (isEnglish) "Choose which apps require the Day Guard mindful gateway." else "اختر التطبيقات التي تتطلب البوابة الواعية لحارس اليوم."',
    'stringResource(R.string.anchor_nfc_authority)': 'if (isEnglish) "Anchor NFC Authority" else "صلاحية NFC للمرساة"',
    'stringResource(R.string.rules_triggers)': 'if (isEnglish) "Rules & Triggers" else "القواعد والمحفزات"',
    'stringResource(R.string.mindful_gateway)': 'if (isEnglish) "Mindful Gateway" else "البوابة الواعية"',
    'stringResource(R.string.interrupt_every_protected_app_with_a_mindful_pause)': 'if (isEnglish) "Interrupt every protected app with a mindful pause." else "قاطع كل تطبيق محمي بوقفة واعية."',
    'stringResource(R.string.water_pause)': 'if (isEnglish) "Water Pause" else "وقفة الماء"',
    'stringResource(R.string.show_a_mandatory_30_second_deep_breath_pause_before_duration_choice)': 'if (isEnglish) "Show a mandatory 30-second deep breath pause before duration choice." else "إظهار وقفة تنفس عميق إلزامية لمدة 30 ثانية قبل اختيار المدة."',
    'stringResource(R.string.optional_overlays_utilities)': 'if (isEnglish) "Optional Overlays & Utilities" else "تراكبات وأدوات مساعدة اختيارية"',
    'stringResource(R.string.total_usage_pill)': 'if (isEnglish) "Total Usage Pill" else "قرص الاستخدام الإجمالي"',
    'stringResource(R.string.show_your_total_screen_time_in_the_overlay)': 'if (isEnglish) "Show your total screen time in the overlay." else "إظهار إجمالي وقت الشاشة في التراكب."',
    'stringResource(R.string.session_usage_overlay)': 'if (isEnglish) "Session Usage Overlay" else "تراكب استخدام الجلسة"',
    'stringResource(R.string.show_a_floating_hud_with_remaining_time_when_an_app_is_allowed)': 'if (isEnglish) "Show a floating HUD with remaining time when an app is allowed." else "إظهار شاشة عرض علوية عائمة مع الوقت المتبقي عند السماح بتطبيق."',
    'stringResource(R.string.early_kick)': 'if (isEnglish) "Early Kick" else "الطرد المبكر"',
    'stringResource(R.string.build_mindfulness_by_ejecting_from_apps_early)': 'if (isEnglish) "Build mindfulness by ejecting from apps early." else "بناء الوعي عن طريق الخروج من التطبيقات مبكرًا."',
    'stringResource(R.string.pause_media)': 'if (isEnglish) "Pause Media" else "إيقاف الوسائط مؤقتًا"',
    'stringResource(R.string.automatically_pause_media_when_an_overlay_appears)': 'if (isEnglish) "Automatically pause media when an overlay appears." else "إيقاف الوسائط تلقائيًا عند ظهور التراكب."',
    'stringResource(R.string.session_duration_policy)': 'if (isEnglish) "Session Duration Policy" else "سياسة مدة الجلسة"',
    'stringResource(R.string.day_guard)': 'if (isEnglish) "Day Guard" else "حارس اليوم"',
    'stringResource(R.string.make_access_smaller_anytime_making_it_larger_requires_the_anchor)': 'if (isEnglish) "Make access smaller anytime. Making it larger requires the Anchor." else "اجعل الوصول أصغر في أي وقت. تكبيره يتطلب المرساة."',
    'stringResource(R.string.app_permissions)': 'if (isEnglish) "App Permissions" else "أذونات التطبيق"',
    'androidx.compose.ui.res.stringResource(com.example.R.string.nfc_not_registered)': 'if (isEnglish) "Register the whiteboard Anchor NFC tag. Keep it at your writing station." else "قم بتسجيل علامة NFC للمرساة. احتفظ بها في محطة الكتابة الخاصة بك."',
    'androidx.compose.ui.res.stringResource(com.example.R.string.nfc_registered)': 'if (isEnglish) "Whiteboard Anchor NFC tag is registered." else "تم تسجيل علامة NFC للمرساة."',
    'androidx.compose.ui.res.stringResource(com.example.R.string.policy_edit_window_active)': 'if (isEnglish) "Policy Edit Window: Active" else "نافذة تعديل السياسة: نشطة"',
    'androidx.compose.ui.res.stringResource(com.example.R.string.policy_edit_window_locked)': 'if (isEnglish) "Policy Edit Window: Locked" else "نافذة تعديل السياسة: مقفلة"',
}

for eng, translated in replacements.items():
    content = content.replace(eng, translated)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
