package com.desktop.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Security.md S2: 12 karakter kuralından geçen ama tahmin edilmesi kolay parolalar reddedilir. */
class PasswordStrengthTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Galatasaray1905", "Fenerbahce1907!", "Besiktas190319", "Password12345", "P@ssw0rd2024!", "Sifre12345678",
            "Şifrem2024!!xy", "İstanbul34Ankara", "Seniseviyorum1", "Turkiye1923!!", "admin1234567x", "Mehmet1985ali",
            "qwerty123456", "1q2w3e4r5t6y", "Asdfgh123456", "abc123abc123", "Aaaa1111bbbb", "zeynep2010zeynep"})
    void weakPasswordsAreRejected(String password) {
        assertNotNull(PasswordStrength.check(password), password);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Kedi-masa-91-Ağaç", "Tz8!vQ2#mN4p", "sabah kahvesi 2 şekerli", "Mavi bisiklet 47 yokuş",
            "Pencere7Limon_Tuz", "Kalite raporu 19 sayfa"})
    void strongPasswordsAreAccepted(String password) {
        assertNull(PasswordStrength.check(password, "Ali Veli", "+905551234567"), password);
    }

    @Test
    void personalInformationIsRejected() {
        assertNotNull(PasswordStrength.check("Kar yağdı 5551234 kez", "Ayşe Yılmaz", "+905551234567"));
        assertNotNull(PasswordStrength.check("Yılmazlar ailesi 77", "Ayşe Yılmaz", "+905551234567"));
        // Kısa isimler (3 harf) her kelimede geçebileceği için aranmaz.
        assertNull(PasswordStrength.check("Kalite raporu 19 sayfa", "Ali Can", "+905551234567"));
    }

    @Test
    void basicRulesStillApply() {
        assertEquals("Parola en az 12 karakter olmalıdır.", PasswordStrength.check("Kısa1parola"));
        assertEquals("Parola hem harf hem rakam içermelidir.", PasswordStrength.check("sadece harflerden oluşan"));
        assertNotNull(PasswordStrength.check("a1234567890123"));
    }

    @Test
    void leetDecodingKeepsTrailingYearsOut() {
        assertEquals("galatasaray", PasswordStrength.deLeet("galatasaray1905"));
        assertEquals("password", PasswordStrength.deLeet("p@ssw0rd2024"));
    }
}
