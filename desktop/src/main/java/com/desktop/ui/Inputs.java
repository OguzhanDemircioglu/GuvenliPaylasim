package com.desktop.ui;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.regex.Pattern;

/**
 * Alan kısıtları: telefon {@code (5xx) xxx xx xx} biçiminde, sadece rakam; ad soyadda rakam yok; WhatsApp ve
 * Authenticator kodlarında sadece rakam. Kısıt yazarken uygulanır: kabul edilmeyen karakter alana hiç girmez.
 */
public final class Inputs {

    /** Harf (Türkçe dahil), boşluk, kesme, tire ve nokta. Sunucudaki kuralla aynı (UserMessages.FULL_NAME_INVALID). */
    private static final Pattern NAME = Pattern.compile("[\\p{L} .'\\-]*");
    /** Kaydedilecek ad soyad: harfle başlar. Sunucudaki {@code FullNames.PATTERN} ile aynı. */
    private static final Pattern FULL_NAME = Pattern.compile("\\p{L}[\\p{L} .'\\-]*");

    private Inputs() {
    }

    public static <T extends TextField> T phone(T field) {
        field.setTextFormatter(new TextFormatter<>(Inputs::phoneChange));
        return field;
    }

    public static <T extends TextField> T digits(T field, int max) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0," + max + "}") ? change : null));
        return field;
    }

    public static <T extends TextField> T name(T field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                NAME.matcher(change.getControlNewText()).matches() && change.getControlNewText().length() <= 100
                        ? change : null));
        return field;
    }

    /** Kaydetmeden önce: geçersizse kullanıcıya gösterilecek mesaj, geçerliyse null. */
    public static String nameError(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            return "Ad soyad zorunludur.";
        }
        if (name.length() > 100) {
            return "Ad soyad en fazla 100 karakter olabilir.";
        }
        return FULL_NAME.matcher(name).matches()
                ? null
                : "Ad soyadda sadece harf, boşluk, kesme, nokta ve tire kullanılabilir.";
    }

    /**
     * Yazılan, yapıştırılan ya da silinen her değişiklikten sonra rakamlar yeniden biçimlenir. Başta 0 ya da +90
     * yazılırsa atılır; ilk hane 5 değilse değişiklik reddedilir. Biçim karakteri silinince önündeki rakam silinir.
     */
    private static TextFormatter.Change phoneChange(TextFormatter.Change change) {
        String oldDigits = change.getControlText().replaceAll("\\D", "");
        String digits = phoneDigits(change.getControlNewText());
        if (digits == null) {
            return null;
        }
        if (change.isDeleted() && !change.isAdded() && digits.equals(oldDigits) && !digits.isEmpty()) {
            digits = digits.substring(0, digits.length() - 1);
        }
        String formatted = formatPhone(digits);
        change.setRange(0, change.getControlText().length());
        change.setText(formatted);
        change.setCaretPosition(formatted.length());
        change.setAnchor(formatted.length());
        return change;
    }

    /** Rakamları çıkarır, +90 ve baştaki 0'ı atar; 10 haneyi aşarsa ya da 5 ile başlamazsa null. */
    static String phoneDigits(String text) {
        String digits = text.replaceAll("\\D", "");
        if (text.trim().startsWith("+90") || (digits.length() > 10 && digits.startsWith("90"))) {
            digits = digits.substring(2);
        }
        while (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10 || (!digits.isEmpty() && digits.charAt(0) != '5')) {
            return null;
        }
        return digits;
    }

    /** {@code 5321234567} → {@code (532) 123 45 67}; eksik rakamlarda yazıldığı kadarı. */
    public static String formatPhone(String digits) {
        if (digits.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("(").append(digits, 0, Math.min(3, digits.length()));
        if (digits.length() >= 3) {
            out.append(')');
        }
        int[] groups = {3, 6, 8, 10};
        for (int g = 0; g < groups.length - 1 && digits.length() > groups[g]; g++) {
            out.append(' ').append(digits, groups[g], Math.min(groups[g + 1], digits.length()));
        }
        return out.toString();
    }
}
