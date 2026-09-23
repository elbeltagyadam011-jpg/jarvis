package com.adam.jarvis;

public final class JarvisIntent {
    public enum Type {
        OPEN_YOUTUBE, OPEN_CHROME, OPEN_SETTINGS, OPEN_WIFI, OPEN_BLUETOOTH, OPEN_PHONE, OPEN_MESSAGES, OPEN_MAPS, OPEN_GALLERY,
        TORCH_ON, TORCH_OFF, VOLUME_UP, VOLUME_DOWN, MEDIA_TOGGLE,
        BATTERY, TIME, DATE, CAMERA, CALCULATOR, ALARM, GOOGLE, CALENDAR, CONTACTS, DOWNLOADS, DISPLAY_SETTINGS, SOUND_SETTINGS, AIRPLANE_SETTINGS, SEARCH_WEB, DEVICE_STATUS,
        GREETING, IDENTITY, UNKNOWN
    }

    public final Type type;
    public final String raw;

    public JarvisIntent(Type type, String raw) {
        this.type = type;
        this.raw = raw;
    }
}
