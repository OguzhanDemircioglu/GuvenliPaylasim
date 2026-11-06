package com.desktop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Kullanıcının yazdığı numara 5XXXXXXXXX'e çevrilir; sunucu sadece bu biçimi kabul eder. */
class PhonesTest {

    @Test
    void commonWritingStylesBecomeTenDigits() {
        assertEquals("5321234567", Phones.normalize("5321234567"));
        assertEquals("5321234567", Phones.normalize("0532 123 45 67"));
        assertEquals("5321234567", Phones.normalize("+90 532 123-45-67"));
        assertEquals("5321234567", Phones.normalize("(0532) 1234567"));
        assertEquals("+905321234567", Phones.toE164(Phones.normalize("0532 123 45 67")));
    }

    @Test
    void nonMobileOrWrongLengthIsRejected() {
        assertNull(Phones.normalize("3121234567"));
        assertNull(Phones.normalize("532123456"));
        assertNull(Phones.normalize("53212345678"));
        assertNull(Phones.normalize("532abc4567"));
        assertNull(Phones.normalize(""));
        assertNull(Phones.normalize(null));
    }
}
