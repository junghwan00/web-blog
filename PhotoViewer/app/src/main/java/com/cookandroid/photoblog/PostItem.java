package com.cookandroid.photoblog;

import android.graphics.Bitmap;

// 서버 /api_root/Post/ 응답 한 건을 표현하는 모델.
// 오프라인 캐시(JSON 저장/복원)와 동기화 양쪽에서 공통으로 사용한다.
public class PostItem {
    public int id;
    public String title;
    public String text;
    public String authorName;
    public String imageUrl;
    public String publishedDate;
    public transient Bitmap bitmap; // 캐시 파일에는 저장하지 않음 (이미지 자체는 별도 파일 캐시)

    public PostItem() {
    }

    public PostItem(int id, String title, String text, String authorName, String imageUrl, String publishedDate) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.authorName = authorName;
        this.imageUrl = imageUrl;
        this.publishedDate = publishedDate;
    }
}
