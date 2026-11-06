package com.desktop.ui;

import com.desktop.service.Phones;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertNull(Inputs.nameError("Ali Rıza O'Neil-Çelik"));
        assertNull(Inputs.nameError("  ali   rıza  çelik "));
        assertNotNull(Inputs.nameError("   "));
        assertNotNull(Inputs.nameError("Ayşe"), "tek kelime yetmez");
        assertNotNull(Inputs.nameError("Ali Rıza Can Çelik"), "dört kelime fazla");
        assertNotNull(Inputs.nameError("Ayşe 2"));
        assertNotNull(Inputs.nameError("5321234567"));
        assertNotNull(Inputs.nameError("-Ayşe Kaya"));
        assertNotNull(Inputs.nameError("A".repeat(60) + " " + "B".repeat(60)));
    }

    @Test
    void nameIsCapitalizedTheTurkishWay() {
        assertEquals("İsmail Işık", Inputs.normalizeName("ismail ışık"));
        assertEquals("Ayşe Yılmaz-Çelik", Inputs.normalizeName("  ayşe   yılmaz-çelik "));
        assertEquals("Şule Öztürk Ünal", Inputs.normalizeName("şule öztürk ünal"));
        assertEquals("McDonald O'neil", Inputs.normalizeName("mcDonald o'neil"), "diğer harflere dokunulmaz");
    }

    @Test
    void whileTypingNamesStayWithinThreeWords() {
        assertTrue(Inputs.typableName("Ali "));
        assertTrue(Inputs.typableName("Ali Rıza Çelik"));
        assertFalse(Inputs.typableName("Ali Rıza Çelik "), "dördüncü kelimeye boşluk açılmaz");
        assertFalse(Inputs.typableName(" Ali"), "başta boşluk yok");
        assertFalse(Inputs.typableName("Ali  Rıza"), "art arda boşluk yok");
        assertFalse(Inputs.typableName("Ali 5"));
    }

    @Test
    void cooldownIsShownAsMinutesAndSeconds() {
        assertEquals("5:00", Cooldown.format(300));
        assertEquals("4:59", Cooldown.format(299));
        assertEquals("0:05", Cooldown.format(5));
    }
}
