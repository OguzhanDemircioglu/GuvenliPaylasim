package com.desktop.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Security.md S2: 12–20 karakter ve dört karakter türü; kuraldan geçen ama tahmin edilmesi kolay parolalar reddedilir. */
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
    @ValueSource(strings = {"Kedi-masa-91-Ağaç", "Tz8!vQ2#mN4p", "Pencere7Limon_Tuz", "Bulut%Kalem3Taş"})
    void strongPasswordsAreAccepted(String password) {
        assertNull(PasswordStrength.check(password, "Ali Veli", "+905551234567"), password);
    }

    @Test
    void personalInformationIsRejected() {
        assertNotNull(PasswordStrength.check("Kar-5551234-Kez", "Ayşe Yılmaz", "+905551234567"));
        assertNotNull(PasswordStrength.check("Yılmazlar-Aile77", "Ayşe Yılmaz", "+905551234567"));
        // Kısa isimler (3 harf) her kelimede geçebileceği için aranmaz.
        assertNull(PasswordStrength.check("Kalite-Raporu19", "Ali Can", "+905551234567"));
    }

    @Test
    void lengthAndCharacterTypesAreRequired() {
        assertEquals("Şifre 12–20 karakter olmalıdır.", PasswordStrength.check("Kalem-Tas99"), "11 karakter");
        assertEquals("Şifre 12–20 karakter olmalıdır.", PasswordStrength.check("Uzun-Parola-12345678x"), "21 karakter");
        assertNull(PasswordStrength.composition("Kalem-Tas999"), "12 karakter yeter");
        assertEquals("Şifrede en az bir büyük harf olmalıdır.", PasswordStrength.composition("kalem-tasi-99"));
        assertEquals("Şifrede en az bir küçük harf olmalıdır.", PasswordStrength.composition("KALEM-TASI-99"));
        assertEquals("Şifrede en az bir rakam olmalıdır.", PasswordStrength.composition("Kalem-Tasi-xx"));
        assertEquals("Şifrede en az bir özel karakter olmalıdır.", PasswordStrength.composition("KalemTasi9999"));
        assertEquals("Şifrede en az bir büyük harf, rakam, özel karakter olmalıdır.",
                PasswordStrength.composition("kalemtasixyzw"));
        assertEquals("Şifrede en az bir özel karakter olmalıdır.", PasswordStrength.composition("Kalem Tas 9999"),
                "boşluk özel karakter sayılmaz");
        assertNull(PasswordStrength.composition("Şule.Işık.2026"), "Türkçe büyük/küçük harfler sayılır");
        assertNull(PasswordStrength.composition("DemoParola2026!"), "demo parolası kurala uyar");
    }

    @Test
    void leetDecodingKeepsTrailingYearsOut() {
        assertEquals("galatasaray", PasswordStrength.deLeet("galatasaray1905"));
        assertEquals("password", PasswordStrength.deLeet("p@ssw0rd2024"));
    }
}
