package com.desktop.ui.views;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** KVKK aydınlatma metni uygulamayla gelir; taslak olduğu ve doldurulacak yerleri açıkça yazar. */
class NoticeTextTest {

    @Test
    void draftNoticeShipsWithTheApp() {
        String text = LoginView.noticeText();
        assertTrue(text.startsWith("TASLAK"), "taslak olduğu ilk satırda yazmalı");
        assertTrue(text.contains("6698 sayılı"));
        assertTrue(text.contains("m.11"), "haklar bölümü olmalı");
        assertTrue(text.contains("[Ad soyad / unvan]"), "veri sorumlusu henüz doldurulmadı");
    }
}
