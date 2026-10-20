package com.cookandroid.photoblog;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

// 서버 주소/로그인 상태/즐겨찾기/작성 초안을 SharedPreferences에 보관.
// 즐겨찾기는 "서버 주소별로 저장"된다는 요구사항(증빙 14-3)에 맞춰 키에 서버 주소를 포함한다.
public final class Prefs {
    private static final String FILE = "photoblog_prefs";
    private static final String KEY_SERVER = "server_address";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN_ENC = "token_enc";
    private static final String DEFAULT_SERVER = "http://10.0.2.2:8000";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getServerAddress() {
        return sp.getString(KEY_SERVER, DEFAULT_SERVER);
    }

    public void setServerAddress(String address) {
        sp.edit().putString(KEY_SERVER, address).apply();
    }

    public String getUsername() {
        return sp.getString(KEY_USERNAME, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    /** 로그인 성공 시 호출: 토큰은 Keystore 키로 암호화해서 저장 */
    public void saveLogin(String username, String token) {
        String enc = CryptoTokenStore.encrypt(token);
        sp.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN_ENC, enc)
                .apply();
    }

    public void logout() {
        sp.edit().remove(KEY_USERNAME).remove(KEY_TOKEN_ENC).apply();
    }

    /** 복호화된 평문 토큰. 로그인 안했거나 복호화 실패 시 null */
    public String getToken() {
        String enc = sp.getString(KEY_TOKEN_ENC, null);
        if (enc == null) return null;
        return CryptoTokenStore.decrypt(enc);
    }

    // --- 즐겨찾기 (서버 주소별) ---
    private String favKey() {
        return "favorites::" + getServerAddress();
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(sp.getStringSet(favKey(), new HashSet<>()));
    }

    public void toggleFavorite(int postId) {
        Set<String> favs = getFavoriteIds();
        String id = String.valueOf(postId);
        if (favs.contains(id)) favs.remove(id); else favs.add(id);
        sp.edit().putStringSet(favKey(), favs).apply();
    }

    public boolean isFavorite(int postId) {
        return getFavoriteIds().contains(String.valueOf(postId));
    }

    // --- 작성 초안 (증빙 14-4) ---
    public void saveDraft(String title, String text, String imagePath) {
        sp.edit()
                .putString("draft_title", title)
                .putString("draft_text", text)
                .putString("draft_image_path", imagePath)
                .apply();
    }

    public void clearDraft() {
        sp.edit().remove("draft_title").remove("draft_text").remove("draft_image_path").apply();
    }

    public boolean hasDraft() {
        return sp.contains("draft_title") || sp.contains("draft_image_path");
    }

    public String getDraftTitle() {
        return sp.getString("draft_title", "");
    }

    public String getDraftText() {
        return sp.getString("draft_text", "");
    }

    public String getDraftImagePath() {
        return sp.getString("draft_image_path", null);
    }
}
