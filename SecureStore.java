package com.adam.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "jarvis_api_key_v1";
    private static final String PREF = "jarvis_secure";
    private static final String VALUE = "api_key";
    private SecureStore() {}

    public static void put(Context c, String plain) {
        try {
            if (plain == null || plain.isEmpty()) { c.getSharedPreferences(PREF,0).edit().remove(VALUE).apply(); return; }
            SecretKey key = getKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(enc, Base64.NO_WRAP);
            c.getSharedPreferences(PREF,0).edit().putString(VALUE, packed).apply();
        } catch (Exception ignored) {}
    }

    public static String get(Context c) {
        try {
            String packed=c.getSharedPreferences(PREF,0).getString(VALUE,"");
            if (packed.isEmpty()) return "";
            String[] p=packed.split("\\.",2);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getKey(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static SecretKey getKey() throws Exception {
        KeyStore ks=KeyStore.getInstance(KS); ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}
