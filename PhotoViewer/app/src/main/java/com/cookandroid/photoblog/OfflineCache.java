package com.cookandroid.photoblog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 증빙 14-3 "오프라인 보관과 즐겨찾기": 마지막으로 불러온 목록(JSON)과 사진을 기기에 저장해
// 네트워크가 없을 때도 볼 수 있게 한다. 목록 캐시 최대 4MB, 사진 캐시 최대 32MB로 제한한다.
public final class OfflineCache {
    private static final String TAG = "OfflineCache";
    private static final long LIST_CACHE_MAX_BYTES = 4L * 1024 * 1024;
    private static final long IMAGE_CACHE_MAX_BYTES = 32L * 1024 * 1024;

    private OfflineCache() {
    }

    private static File listCacheFile(Context context, String serverAddress) {
        return new File(context.getFilesDir(), "list_cache_" + hash(serverAddress) + ".json");
    }

    private static File imageCacheDir(Context context) {
        File dir = new File(context.getFilesDir(), "image_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void saveListCache(Context context, String serverAddress, List<PostItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (PostItem item : items) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("title", item.title);
                o.put("text", item.text);
                o.put("author_name", item.authorName);
                o.put("image", item.imageUrl);
                o.put("published_date", item.publishedDate);
                arr.put(o);
            }
            byte[] data = arr.toString().getBytes("UTF-8");
            if (data.length > LIST_CACHE_MAX_BYTES) {
                Log.w(TAG, "목록 캐시가 4MB를 넘어 저장을 건너뜀: " + data.length);
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(listCacheFile(context, serverAddress))) {
                fos.write(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "목록 캐시 저장 실패", e);
        }
    }

    public static List<PostItem> loadListCache(Context context, String serverAddress) {
        List<PostItem> items = new ArrayList<>();
        File file = listCacheFile(context, serverAddress);
        if (!file.exists()) return items;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            JSONArray arr = new JSONArray(new String(data, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                items.add(ApiClient.parsePost(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "목록 캐시 불러오기 실패", e);
        }
        return items;
    }

    public static void cacheImage(Context context, String imageUrl, Bitmap bitmap) {
        if (imageUrl == null || imageUrl.isEmpty() || bitmap == null) return;
        try {
            File dir = imageCacheDir(context);
            enforceImageCacheLimit(dir);
            File out = new File(dir, hash(imageUrl) + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
        } catch (Exception e) {
            Log.e(TAG, "사진 캐시 저장 실패", e);
        }
    }

    public static Bitmap loadCachedImage(Context context, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        File file = new File(imageCacheDir(context), hash(imageUrl) + ".png");
        if (!file.exists()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private static void enforceImageCacheLimit(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= IMAGE_CACHE_MAX_BYTES) return;
        // 오래된 파일부터 삭제해서 32MB 이하로 유지
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : sorted) {
            if (total <= IMAGE_CACHE_MAX_BYTES) break;
            total -= f.length();
            f.delete();
        }
    }

    private static String hash(String s) {
        return String.valueOf(s.hashCode());
    }
}
