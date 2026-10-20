package com.cookandroid.photoblog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// 공통평가 01 - Android 로컬 JUnit 테스트 (app/src/test, 안드로이드 프레임워크 불필요)
// #14-1 검색/정렬/페이지네이션 URL 생성 로직(ApiClient.buildListUrl)을 단위 테스트로 검증.
public class ApiClientUrlTest {

    @Test
    public void noParams_returnsPlainListUrl() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000", null, null);
        assertEquals("http://10.0.2.2:8000/api_root/Post/", url);
    }

    @Test
    public void trailingSlashInBaseUrl_isNormalized() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000/", null, null);
        assertEquals("http://10.0.2.2:8000/api_root/Post/", url);
    }

    @Test
    public void searchOnly_addsSearchParam() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000", "해변", null);
        assertTrue(url.startsWith("http://10.0.2.2:8000/api_root/Post/?search="));
        assertTrue(url.contains("search="));
    }

    @Test
    public void orderingOnly_addsOrderingParam() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000", null, "title");
        assertEquals("http://10.0.2.2:8000/api_root/Post/?ordering=title", url);
    }

    @Test
    public void searchAndOrdering_areJoinedWithAmpersand() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000", "a", "title");
        assertTrue(url.contains("search=a"));
        assertTrue(url.contains("&ordering=title"));
    }

    @Test
    public void emptySearch_isIgnored() {
        String url = ApiClient.buildListUrl("http://10.0.2.2:8000", "", "");
        assertEquals("http://10.0.2.2:8000/api_root/Post/", url);
    }
}
