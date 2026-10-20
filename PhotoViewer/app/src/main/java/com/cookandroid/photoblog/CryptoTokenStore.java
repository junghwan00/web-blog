package com.cookandroid.photoblog;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

// 증빙 14-5 "서버 및 계정 설정" 편의기능: 로그인 토큰을 평문으로 저장하지 않고
// Android Keystore에 보관된 키로 AES/GCM 암호화해 SharedPreferences에 저장한다.
public final class CryptoTokenStore {
    private static final String TAG = "CryptoTokenStore";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "photoblog_token_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private CryptoTokenStore() {
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(spec);
            keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    /** 평문 토큰 -> "base64(iv) : base64(ciphertext+tag)" 형태 문자열로 암호화 */
    public static String encrypt(String plainText) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipherBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "토큰 암호화 실패", e);
            return null;
        }
    }

    /** encrypt()로 저장된 문자열을 복호화해 평문 토큰 반환. 실패하면 null */
    public static String decrypt(String stored) {
        if (stored == null || !stored.contains(":")) return null;
        try {
            String[] parts = stored.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP);
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "토큰 복호화 실패 (키 변경/삭제 가능성)", e);
            return null;
        }
    }
}
