package com.cookandroid.photoblog;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

// 증빙 14-5 "서버 및 계정 설정": 서버 주소 변경, 연결 확인, 로그인/로그아웃.
// 로그인 토큰은 CryptoTokenStore로 암호화되어 Prefs에 저장된다.
public final class SettingsDialog {

    public interface OnChangedListener {
        void onChanged();
    }

    private SettingsDialog() {
    }

    public static void show(Context context, Prefs prefs, OnChangedListener listener) {
        Handler handler = new Handler(Looper.getMainLooper());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        root.setPadding(pad, pad, pad, pad);

        TextView addrLabel = new TextView(context);
        addrLabel.setText("서버 주소");
        root.addView(addrLabel);

        EditText addrInput = new EditText(context);
        addrInput.setHint("예: http://10.0.2.2:8000 또는 https://아이디.pythonanywhere.com");
        addrInput.setText(prefs.getServerAddress());
        root.addView(addrInput);

        TextView statusText = new TextView(context);
        statusText.setPadding(0, dp(context, 8), 0, dp(context, 8));
        updateStatus(statusText, prefs);
        root.addView(statusText);

        Button checkBtn = new Button(context);
        checkBtn.setText("연결 확인");
        root.addView(checkBtn);

        TextView userLabel = new TextView(context);
        userLabel.setText("아이디");
        userLabel.setPadding(0, dp(context, 12), 0, 0);
        root.addView(userLabel);

        EditText userInput = new EditText(context);
        if (prefs.getUsername() != null) userInput.setText(prefs.getUsername());
        root.addView(userInput);

        TextView passLabel = new TextView(context);
        passLabel.setText("비밀번호 (저장하지 않음)");
        root.addView(passLabel);

        EditText passInput = new EditText(context);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passInput);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(context, 12), 0, 0);
        Button loginBtn = new Button(context);
        loginBtn.setText("로그인");
        Button logoutBtn = new Button(context);
        logoutBtn.setText("로그아웃");
        buttonRow.addView(loginBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        buttonRow.addView(logoutBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttonRow);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("서버 설정 · 로그인")
                .setView(root)
                .setPositiveButton("주소 저장", (d, w) -> {
                    prefs.setServerAddress(addrInput.getText().toString().trim());
                    if (listener != null) listener.onChanged();
                })
                .setNegativeButton("닫기", null)
                .create();

        checkBtn.setOnClickListener(v -> {
            String addr = addrInput.getText().toString().trim();
            statusText.setText("연결 확인 중...");
            new Thread(() -> {
                ApiClient.PageResult result = ApiClient.fetchPage(addr, null, null, null);
                handler.post(() -> {
                    if (result.error == null) {
                        statusText.setText("연결 성공 · 게시물 " + result.count + "개 확인");
                        statusText.setTextColor(Color.parseColor("#2E7D32"));
                    } else {
                        statusText.setText(result.error);
                        statusText.setTextColor(Color.parseColor("#C62828"));
                    }
                });
            }).start();
        });

        loginBtn.setOnClickListener(v -> {
            String addr = addrInput.getText().toString().trim();
            String username = userInput.getText().toString().trim();
            String password = passInput.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "아이디/비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.setServerAddress(addr);
            statusText.setText("로그인 중...");
            new Thread(() -> {
                ApiClient.LoginResult result = ApiClient.login(addr, username, password);
                handler.post(() -> {
                    if (result.success) {
                        prefs.saveLogin(username, result.token);
                        updateStatus(statusText, prefs);
                        Toast.makeText(context, "로그인 완료: " + username, Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onChanged();
                    } else {
                        statusText.setText(result.error);
                        statusText.setTextColor(Color.parseColor("#C62828"));
                    }
                });
            }).start();
        });

        logoutBtn.setOnClickListener(v -> {
            prefs.logout();
            updateStatus(statusText, prefs);
            if (listener != null) listener.onChanged();
        });

        dialog.show();
    }

    private static void updateStatus(TextView statusText, Prefs prefs) {
        if (prefs.isLoggedIn()) {
            statusText.setText("로그인 완료 · " + prefs.getUsername());
            statusText.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            statusText.setText("로그인이 필요합니다 (글쓰기/수정/삭제에 필요)");
            statusText.setTextColor(Color.parseColor("#757575"));
        }
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
