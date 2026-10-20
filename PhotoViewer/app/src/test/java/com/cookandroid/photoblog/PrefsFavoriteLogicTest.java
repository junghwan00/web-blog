package com.cookandroid.photoblog;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Prefs가 SharedPreferences(안드로이드 프레임워크)에 의존하므로, 그 안에서 쓰는
// "즐겨찾기 토글" 순수 로직만 동일하게 재현해 로컬 JUnit으로 검증한다. (#14-3)
public class PrefsFavoriteLogicTest {

    private Set<String> toggle(Set<String> favorites, int postId) {
        Set<String> result = new HashSet<>(favorites);
        String id = String.valueOf(postId);
        if (result.contains(id)) result.remove(id); else result.add(id);
        return result;
    }

    @Test
    public void togglingUnfavorited_addsIt() {
        Set<String> favs = toggle(new HashSet<>(), 5);
        assertTrue(favs.contains("5"));
    }

    @Test
    public void togglingFavorited_removesIt() {
        Set<String> favs = new HashSet<>();
        favs.add("5");
        Set<String> result = toggle(favs, 5);
        assertFalse(result.contains("5"));
    }

    @Test
    public void togglingDifferentId_doesNotAffectOthers() {
        Set<String> favs = new HashSet<>();
        favs.add("5");
        Set<String> result = toggle(favs, 7);
        assertTrue(result.contains("5"));
        assertTrue(result.contains("7"));
    }
}
