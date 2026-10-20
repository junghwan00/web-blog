package com.cookandroid.photoblog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 서버(/api_root/...) 와 통신하는 모든 네트워크 호출을 한 곳에 모은 클래스.
// MainActivity는 이 클래스만 호출하고, 백그라운드 스레드에서 실행해야 한다 (호출부에서 executor 사용).
public final class ApiClient {
    private static final String TAG = "ApiClient";

    public static class PageResult {
        public List<PostItem> items = new ArrayList<>();
        public String nextPageUrl;
        public int count;
        public String error;
    }

    public static class ActionResult {
        public boolean success;
        public int httpCode;
        public String error;
        public PostItem item;
    }

    private ApiClient() {
    }

    private static String listUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/api_root/Post/";
    }

    // #14-1 검색/정렬/페이지네이션: search, ordering 파라미터를 붙여 첫 페이지를 요청하거나,
    // 서버가 돌려준 next 링크(nextPageUrl)를 그대로 다시 요청해 다음 페이지를 이어받는다.
    // 순수 함수로 분리: 안드로이드 프레임워크 없이도 로컬 JUnit(app/src/test)에서 바로 테스트 가능.
    public static String buildListUrl(String baseUrl, String search, String ordering) {
        StringBuilder sb = new StringBuilder(listUrl(baseUrl));
        List<String> params = new ArrayList<>();
        try {
            if (search != null && !search.isEmpty()) {
                params.add("search=" + URLEncoder.encode(search, "UTF-8"));
            }
            if (ordering != null && !ordering.isEmpty()) {
                params.add("ordering=" + URLEncoder.encode(ordering, "UTF-8"));
            }
        } catch (Exception ignored) {
        }
        if (!params.isEmpty()) {
            sb.append("?").append(String.join("&", params));
        }
        return sb.toString();
    }

    public static PageResult fetchPage(String baseUrl, String nextPageUrl, String search, String ordering) {
        PageResult result = new PageResult();
        String urlStr = (nextPageUrl != null) ? nextPageUrl : buildListUrl(baseUrl, search, ordering);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                result.error = "서버 응답 오류 (HTTP " + code + ")";
                return result;
            }
            String body = readAll(connection.getInputStream());
            JSONObject root = new JSONObject(body);
            result.count = root.optInt("count", 0);
            result.nextPageUrl = root.isNull("next") ? null : root.optString("next", null);
            JSONArray results = root.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                result.items.add(parsePost(results.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchPage 실패", e);
            result.error = "동기화 실패: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    public static PostItem parsePost(JSONObject o) throws JSONException {
        return new PostItem(
                o.optInt("id"),
                o.optString("title", ""),
                o.optString("text", ""),
                o.optString("author_name", ""),
                o.optString("image", ""),
                o.optString("published_date", "")
        );
    }

    public static Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            InputStream is = conn.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "downloadBitmap 실패: " + imageUrl, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // --- 로그인 (#14-5) ---
    public static class LoginResult {
        public boolean success;
        public String token;
        public String error;
    }

    public static LoginResult login(String baseUrl, String username, String password) {
        LoginResult result = new LoginResult();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl.replaceAll("/+$", "") + "/api_root/login/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String body = "username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8");
            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                out.writeBytes(body);
            }

            int code = connection.getResponseCode();
            InputStream stream = (code == HttpURLConnection.HTTP_OK)
                    ? connection.getInputStream() : connection.getErrorStream();
            String respBody = readAll(stream);
            if (code == HttpURLConnection.HTTP_OK) {
                result.success = true;
                result.token = new JSONObject(respBody).optString("token", null);
            } else {
                result.error = "로그인 실패 (HTTP " + code + "): " + respBody;
            }
        } catch (Exception e) {
            Log.e(TAG, "login 실패", e);
            result.error = "로그인 실패: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    // --- 게시글 작성 (#3,#10) ---
    public static ActionResult createPost(String baseUrl, String token, String title, String text, String imagePath) {
        ActionResult result = new ActionResult();
        String boundary = "----PhotoBlogBoundary" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        HttpURLConnection connection = null;
        try {
            URL url = new URL(listUrl(baseUrl));
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            connection.setRequestProperty("Authorization", "Token " + token);

            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                writeFormField(out, boundary, "title", title);
                writeFormField(out, boundary, "text", text);
                if (imagePath != null) {
                    File imageFile = new File(imagePath);
                    out.writeBytes("--" + boundary + lineEnd);
                    out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\""
                            + imageFile.getName() + "\"" + lineEnd);
                    out.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);
                    try (FileInputStream fis = new FileInputStream(imageFile)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = fis.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    out.writeBytes(lineEnd);
                }
                out.writeBytes("--" + boundary + "--" + lineEnd);
            }

            int code = connection.getResponseCode();
            result.httpCode = code;
            InputStream stream = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (code == 200 || code == 201) {
                result.success = true;
                result.item = parsePost(new JSONObject(body));
            } else {
                result.error = "업로드 실패 (HTTP " + code + "): " + body;
            }
        } catch (Exception e) {
            Log.e(TAG, "createPost 실패", e);
            result.error = "업로드 실패: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    // --- 게시글 수정 (#14-2, 본인 글만) ---
    public static ActionResult updatePost(String baseUrl, String token, int postId, String title, String text) {
        ActionResult result = new ActionResult();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(listUrl(baseUrl) + postId + "/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Authorization", "Token " + token);

            String body = "title=" + URLEncoder.encode(title, "UTF-8")
                    + "&text=" + URLEncoder.encode(text, "UTF-8");
            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                out.writeBytes(body);
            }

            int code = connection.getResponseCode();
            result.httpCode = code;
            InputStream stream = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            String respBody = readAll(stream);
            if (code == 200) {
                result.success = true;
                result.item = parsePost(new JSONObject(respBody));
            } else {
                result.error = "수정 실패 (HTTP " + code + "): " + respBody;
            }
        } catch (Exception e) {
            Log.e(TAG, "updatePost 실패", e);
            result.error = "수정 실패: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    // --- 게시글 삭제 (#14-2, 본인 글만) ---
    public static ActionResult deletePost(String baseUrl, String token, int postId) {
        ActionResult result = new ActionResult();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(listUrl(baseUrl) + postId + "/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Authorization", "Token " + token);

            int code = connection.getResponseCode();
            result.httpCode = code;
            if (code == 204 || code == 200) {
                result.success = true;
            } else {
                InputStream stream = connection.getErrorStream();
                result.error = "삭제 실패 (HTTP " + code + "): " + (stream != null ? readAll(stream) : "");
            }
        } catch (Exception e) {
            Log.e(TAG, "deletePost 실패", e);
            result.error = "삭제 실패: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private static void writeFormField(DataOutputStream out, String boundary, String name, String value) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value + "\r\n");
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        is.close();
        return sb.toString();
    }

    public static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.KOREA).format(new Date());
    }
}
