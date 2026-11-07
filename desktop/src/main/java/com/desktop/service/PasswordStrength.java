package com.desktop.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Zayıf parola kontrolü (docs/Security.md S2). Parola sunucuya gitmediği için kontrol bu bilgisayarda yapılır.
 * <p>
 * Önce uzunluk ve karakter türü kuralı ({@link #composition}). Bu kuraldan geçen zayıf parolalar çoğunlukla şu türdendir: bilinen bir kelime ya da
 * isim artı yıl/rakam ({@code Galatasaray1905}), klavye ya da sıra dizileri ({@code 1q2w3e4r5t6y}), aynı karakterin
 * tekrarı, kişinin kendi adı ya da telefonu. Kontrol, parolanın harf kısmını Türkçe harfleri sadeleştirip ve rakamla
 * yazılmış harfleri ({@code 0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s}) çözüp gömülü sözlükle karşılaştırır.
 * <p>
 * Sınır: değiştirilmiş bir istemci bu kontrolü atlayabilir; kontrol kullanıcıyı kendi zayıf seçiminden korur.
 */
public final class PasswordStrength {

    /**
     * Uzunluk ve karakter türü kuralı (2026-11-07 kararı): 12–20 karakter; büyük, küçük harf, rakam, özel karakter.
     * Parola özel anahtarların tek kilidi olduğu için en az 12 korunur (Security.md S2).
     */
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 20;
    public static final String RULE = "12–20 karakter; büyük harf, küçük harf, rakam ve özel karakter";
    private static final Set<String> WORDS = load();
    /** Klavye satırları ve sıralar; tersleri de aranır. */
    private static final List<String> SEQUENCES = List.of(
            "0123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjkl", "zxcvbnm",
            "qwertzuiop", "1q2w3e4r5t6y7u8i9o0p", "1qaz2wsx3edc4rfv5tgb", "qazwsxedcrfvtgbyhn", "1234qwer", "q1w2e3r4t5y6");
    private static final int SEQUENCE_RUN = 5;

    private PasswordStrength() {
    }

    /**
     * Uygun değilse kullanıcıya gösterilecek Türkçe mesaj, uygunsa null.
     *
     * @param personal kişinin adı soyadı, telefonu gibi parolada geçmemesi gereken bilgiler (null olabilir)
     */
    public static String check(String password, String... personal) {
        String composition = composition(password);
        if (composition != null) {
            return composition;
        }
        if (password.chars().distinct().count() < 6) {
            return "Parolada aynı karakterler çok fazla tekrar ediyor.";
        }
        String lower = password.toLowerCase(Locale.forLanguageTag("tr"));
        if (hasRun(lower)) {
            return "Parola aynı karakterin uzun tekrarını içeriyor.";
        }
        if (hasSequence(simplify(lower))) {
            return "Parola klavye ya da sıra dizisi içeriyor (ör. 12345, qwerty, 1q2w3e). Tahmin edilmesi kolaydır.";
        }
        String personalHit = personalHit(lower, personal);
        if (personalHit != null) {
            return personalHit;
        }
        String simple = simplify(lower);
        String letters = simple.replaceAll("[^a-z]", "");
        if (letters.length() < 4) {
            return "Parola neredeyse sadece rakamlardan oluşuyor. Birkaç kelime kullanın.";
        }
        if (isCommon(letters) || isCommon(deLeet(simple))) {
            return "Parola çok bilinen bir kelime ya da isme rakam eklenmiş hali. Birbiriyle ilgisiz birkaç kelime kullanın.";
        }
        return null;
    }

    /**
     * Sadece uzunluk ve karakter türleri: girişte de aranır (kurallar herkes için geçerli, 2026-11-07). Özel karakter:
     * harf, rakam ya da boşluk olmayan her karakter. Uygunsa null, değilse eksik olanı söyleyen mesaj.
     */
    public static String composition(String password) {
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "Parola " + MIN_LENGTH + "–" + MAX_LENGTH + " karakter olmalıdır.";
        }
        List<String> missing = new java.util.ArrayList<>();
        if (password.chars().noneMatch(Character::isUpperCase)) {
            missing.add("büyük harf");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            missing.add("küçük harf");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            missing.add("rakam");
        }
        if (password.chars().noneMatch(PasswordStrength::isSpecial)) {
            missing.add("özel karakter");
        }
        return missing.isEmpty() ? null : "Parolada en az bir " + String.join(", ", missing) + " olmalıdır.";
    }

    static boolean isSpecial(int c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }

    /**
     * Harf kısmı bir sözlük kelimesi, kelimenin tekrarı, iki sözlük kelimesinin birleşimi ya da 5+ harfli bir sözlük
     * kelimesine en fazla 3 harf eklenmiş hali mi?
     */
    static boolean isCommon(String letters) {
        if (WORDS.contains(letters)) {
            return true;
        }
        for (String word : WORDS) {
            if (word.length() >= 3 && letters.length() % word.length() == 0 && word.repeat(letters.length() / word.length()).equals(letters)) {
                return true;
            }
        }
        for (String word : WORDS) {
            if (word.length() >= 5 && letters.length() - word.length() <= 3
                    && (letters.startsWith(word) || letters.endsWith(word))) {
                return true;
            }
        }
        for (int i = 3; i <= letters.length() - 3; i++) {
            if (WORDS.contains(letters.substring(0, i)) && WORDS.contains(letters.substring(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRun(String text) {
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            run = text.charAt(i) == text.charAt(i - 1) ? run + 1 : 1;
            if (run >= 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSequence(String text) {
        for (String sequence : SEQUENCES) {
            for (String candidate : List.of(sequence, new StringBuilder(sequence).reverse().toString())) {
                for (int i = 0; i + SEQUENCE_RUN <= candidate.length(); i++) {
                    if (text.contains(candidate.substring(i, i + SEQUENCE_RUN))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String personalHit(String lower, String... personal) {
        if (personal == null) {
            return null;
        }
        String plain = simplify(lower);
        for (String info : personal) {
            if (info == null || info.isBlank()) {
                continue;
            }
            String digits = info.replaceAll("\\D", "");
            if (digits.length() >= 10) {
                String mobile = digits.substring(digits.length() - 10);
                for (int i = 0; i + 6 <= mobile.length(); i++) {
                    if (lower.contains(mobile.substring(i, i + 6))) {
                        return "Parola telefon numaranızın bir kısmını içeriyor.";
                    }
                }
            }
            for (String part : simplify(info.toLowerCase(Locale.forLanguageTag("tr"))).split("[^a-z]+")) {
                if (part.length() >= 4 && plain.contains(part)) {
                    return "Parola adınızı ya da soyadınızı içeriyor.";
                }
            }
        }
        return null;
    }

    /** Türkçe harfleri ASCII'ye indirir. */
    static String simplify(String text) {
        return text.replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o')
                .replace('ş', 's').replace('ü', 'u').replace('â', 'a').replace('î', 'i').replace('û', 'u');
    }

    /**
     * Rakam ve simgeyle yazılmış harfleri çözer ({@code P@ssw0rd} → {@code password}) ve sadece harfleri döner. Yalnızca
     * harf içeren parçalardaki karakterler çözülür; parçanın sonundaki rakamlar yıl ya da sayı sayılıp atılır
     * ({@code Galatasaray1905} → {@code galatasaray}).
     */
    static String deLeet(String text) {
        StringBuilder letters = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = i;
            while (i < text.length() && (isAsciiLetter(text.charAt(i)) || LEET.indexOf(text.charAt(i)) >= 0)) {
                i++;
            }
            String run = text.substring(start, i);
            int end = run.length();
            while (end > 0 && Character.isDigit(run.charAt(end - 1))) {
                end--;
            }
            run = run.substring(0, end);
            if (run.chars().anyMatch(c -> isAsciiLetter((char) c))) {
                for (char c : run.toCharArray()) {
                    int leet = LEET.indexOf(c);
                    letters.append(leet >= 0 ? LEET_AS.charAt(leet) : c);
                }
            }
            if (i == start) {
                i++;
            }
        }
        return letters.toString();
    }

    private static final String LEET = "013457@$!";
    private static final String LEET_AS = "oieastasi";

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>();
        try (InputStream in = PasswordStrength.class.getResourceAsStream("/com/desktop/weak-words.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    words.add(word);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(words);
    }
}
