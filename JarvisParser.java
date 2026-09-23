package com.adam.jarvis;

import java.util.*;
import java.text.Normalizer;

public final class JarvisParser {
    private JarvisParser() {}

    public static List<String> splitCommands(String input) {
        String s = input.replace("،", ",").replace("؛", ";");
        s = s.replace(" وبعدها ", ";").replace(" ثم ", ";")
             .replace(" وبعدين ", ";").replace(" and then ", ";")
             .replace(" then ", ";");
        String[] parts = s.split("[,;]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    public static JarvisIntent parse(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();

        if (has(s,"اهلا","أهلا","مرحبا","السلام عليكم","hello","hi","هاي"))
            return new JarvisIntent(JarvisIntent.Type.GREETING, raw);
        if (has(s,"مين انت","من انت","who are you"))
            return new JarvisIntent(JarvisIntent.Type.IDENTITY, raw);

        if (has(s,"افتح يوتيوب","يوتيوب","youtube"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_YOUTUBE, raw);
        if (has(s,"افتح كروم","كروم","chrome"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_CHROME, raw);
        if (has(s,"افتح التليفون","افتح الهاتف","الهاتف","التليفون","phone","dialer"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_PHONE, raw);
        if (has(s,"افتح الرسائل","الرسائل","messages","messaging"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_MESSAGES, raw);
        if (has(s,"افتح الخرائط","الخريطة","maps","google maps"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_MAPS, raw);
        if (has(s,"افتح المعرض","الصور","gallery","photos"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_GALLERY, raw);
        if (has(s,"افتح الاعدادات","افتح الإعدادات","الاعدادات","الإعدادات","settings"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_SETTINGS, raw);
        if (has(s,"واي فاي","wifi","wi-fi"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_WIFI, raw);
        if (has(s,"بلوتوث","bluetooth"))
            return new JarvisIntent(JarvisIntent.Type.OPEN_BLUETOOTH, raw);

        if (has(s,"شغل الكشاف","ولع الكشاف","شغل الفلاش","flashlight on","torch on"))
            return new JarvisIntent(JarvisIntent.Type.TORCH_ON, raw);
        if (has(s,"اقفل الكشاف","اطفي الكشاف","اطفئ الكشاف","اقفل الفلاش","flashlight off","torch off"))
            return new JarvisIntent(JarvisIntent.Type.TORCH_OFF, raw);

        if (has(s,"ارفع الصوت","علي الصوت","على الصوت","volume up","increase volume"))
            return new JarvisIntent(JarvisIntent.Type.VOLUME_UP, raw);
        if (has(s,"وطي الصوت","وطي الصوت","خفض الصوت","اخفض الصوت","volume down","decrease volume"))
            return new JarvisIntent(JarvisIntent.Type.VOLUME_DOWN, raw);

        if (has(s,"شغل او وقف","شغل ووقف","وقف الموسيقى","شغل الموسيقى","play pause","play","pause"))
            return new JarvisIntent(JarvisIntent.Type.MEDIA_TOGGLE, raw);

        if (has(s,"البطارية","البطاريه","battery"))
            return new JarvisIntent(JarvisIntent.Type.BATTERY, raw);
        if (has(s,"الساعة","الوقت","time"))
            return new JarvisIntent(JarvisIntent.Type.TIME, raw);
        if (has(s,"التاريخ","النهار","date","today"))
            return new JarvisIntent(JarvisIntent.Type.DATE, raw);
        if (has(s,"افتح الكاميرا","الكاميرا","camera"))
            return new JarvisIntent(JarvisIntent.Type.CAMERA, raw);
        if (has(s,"آلة حاسبة","الحاسبة","calculator","احسب"))
            return new JarvisIntent(JarvisIntent.Type.CALCULATOR, raw);
        if (has(s,"اضبط منبه","منبه","alarm"))
            return new JarvisIntent(JarvisIntent.Type.ALARM, raw);
        if (has(s,"افتح جوجل","google"))
            return new JarvisIntent(JarvisIntent.Type.GOOGLE, raw);
        if (has(s,"افتح التقويم","التقويم","calendar","agenda"))
            return new JarvisIntent(JarvisIntent.Type.CALENDAR, raw);
        if (has(s,"افتح جهات الاتصال","جهات الاتصال","contacts","contact"))
            return new JarvisIntent(JarvisIntent.Type.CONTACTS, raw);
        if (has(s,"افتح التنزيلات","التنزيلات","downloads"))
            return new JarvisIntent(JarvisIntent.Type.DOWNLOADS, raw);
        if (has(s,"اعدادات الشاشة","إعدادات الشاشة","display settings","سطوع الشاشة"))
            return new JarvisIntent(JarvisIntent.Type.DISPLAY_SETTINGS, raw);
        if (has(s,"اعدادات الصوت","إعدادات الصوت","sound settings"))
            return new JarvisIntent(JarvisIntent.Type.SOUND_SETTINGS, raw);
        if (has(s,"وضع الطيران","الطيران","airplane mode","flight mode"))
            return new JarvisIntent(JarvisIntent.Type.AIRPLANE_SETTINGS, raw);
        if (has(s,"ابحث عن","دور على","ابحث في جوجل","search for","search google"))
            return new JarvisIntent(JarvisIntent.Type.SEARCH_WEB, raw);
        if (has(s,"حالة الجهاز","معلومات الجهاز","device status","system status"))
            return new JarvisIntent(JarvisIntent.Type.DEVICE_STATUS, raw);

        return new JarvisIntent(JarvisIntent.Type.UNKNOWN, raw);
    }

    private static boolean has(String s, String... xs) {
        for (String x : xs) if (s.contains(x.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
