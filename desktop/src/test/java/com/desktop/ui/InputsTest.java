package com.desktop.ui;

import com.desktop.service.Phones;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Telefon alanı (5xx) xxx xx xx biçiminde, sadece rakam; ad soyad harfle. */
class InputsTest {

    @Test
    void phoneIsFormattedAsTyped() {
        assertEquals("", Inputs.formatPhone(""));
        assertEquals("(5", Inputs.formatPhone("5"));
        assertEquals("(53", Inputs.formatPhone("53"));
        assertEquals("(532)", Inputs.formatPhone("532"));
        assertEquals("(532) 1", Inputs.formatPhone("5321"));
        assertEquals("(532) 123", Inputs.formatPhone("532123"));
        assertEquals("(532) 123 4", Inputs.formatPhone("5321234"));
        assertEquals("(532) 123 45 6", Inputs.formatPhone("532123456"));
        assertEquals("(532) 123 45 67", Inputs.formatPhone("5321234567"));
    }

    @Test
    void formattedPhoneIsAcceptedByServerRules() {
        assertEquals("5321234567", Phones.normalize("(532) 123 45 67"));
        assertEquals("(532) 123 45 67", Formats.phone("+905321234567"));
    }

    @Test
    void phoneKeepsOnlyMobileDigits() {
        assertEquals("5321234567", Inputs.phoneDigits("(532) 123 45 67"));
        assertEquals("5321234567", Inputs.phoneDigits("0532 123 45 67"));
        assertEquals("5321234567", Inputs.phoneDigits("+90 532 123 45 67"));
        assertEquals("5321234567", Inputs.phoneDigits("905321234567"));
        assertEquals("532", Inputs.phoneDigits("5a3b2"));
        assertEquals("", Inputs.phoneDigits("0"));
        assertNull(Inputs.phoneDigits("3121234567"));
        assertNull(Inputs.phoneDigits("53212345678"));
    }

    @Test
    void nameRules() {
        assertNull(Inputs.nameError("Ayşe Yılmaz"));
        assertNull(Inputs.nameError("Ali Rıza O'Neil-Çelik Jr."));
        assertNotNull(Inputs.nameError("   "));
        assertNotNull(Inputs.nameError("Ayşe 2"));
        assertNotNull(Inputs.nameError("5321234567"));
        assertNotNull(Inputs.nameError("-Ayşe"));
        assertNotNull(Inputs.nameError("A".repeat(101)));
    }
}
