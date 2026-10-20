package com.cookandroid.photoblog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PostItemTest {
    @Test
    public void fieldsAreSetByConstructor() {
        PostItem item = new PostItem(3, "제목", "내용", "student", "http://x/y.jpg", "2026-10-20");
        assertEquals(3, item.id);
        assertEquals("제목", item.title);
        assertEquals("내용", item.text);
        assertEquals("student", item.authorName);
        assertEquals("http://x/y.jpg", item.imageUrl);
        assertEquals("2026-10-20", item.publishedDate);
    }

    @Test
    public void bitmapIsNullByDefault() {
        PostItem item = new PostItem(1, "a", "b", "c", "d", "e");
        assertNull(item.bitmap);
    }
}
