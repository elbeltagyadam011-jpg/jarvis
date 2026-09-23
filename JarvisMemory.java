package com.adam.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

public final class JarvisMemory {
    private final SharedPreferences p;

    public JarvisMemory(Context c) {
        p = c.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE);
    }

    public void setLastCommand(String value) {
        p.edit().putString("last_command", value).apply();
    }

    public String getLastCommand() {
        return p.getString("last_command", "");
    }

    public void setNickname(String value) {
        p.edit().putString("nickname", value).apply();
    }

    public String getNickname() {
        return p.getString("nickname", "Sir");
    }
}
