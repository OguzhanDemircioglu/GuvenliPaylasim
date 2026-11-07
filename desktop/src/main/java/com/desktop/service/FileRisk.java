package com.desktop.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Dosyanın riskini uzantıya değil içeriğe bakarak sınıflar (docs/UI_Standards.md §4.5, Security.md S4). Dosyalar uçtan
 * uca şifreli olduğu için sunucu tarama yapamaz; kontrol gönderende (program engellenir) ve alıcıda (açılan içerik
 * yeniden denetlenir, riskliyse kaydetmeden önce uyarılır) yapılır. Alıcı göndericinin kontrolüne güvenmez: değiştirilmiş
 * bir istemci gönderen kontrolünü atlayabilir.
 * <p>
 * Sezgiseldir: bir dosyanın güvenli olduğunu kanıtlamaz (ör. okuyucudaki bir açığı kullanan bozuk PDF), yaygın saldırı
 * türlerini yakalar. Kaydedilen dosya ayrıca Windows'un dış kaynak etiketini alır (Windows ve Office korumaları devreye
 * girer).
 */
public final class FileRisk {

    public enum Level {
        /** Bilinen bir risk bulunmadı. */
        SAFE,
        /** Açılmadan önce dikkat: makro, PDF'te betik, içinde program olan arşiv, uzantıyla uyuşmayan içerik. */
        RISKY,
        /** Çalıştırılabilir program ya da betik: gönderilemez; alıcıya gelirse en güçlü uyarı verilir. */
        BLOCKED
    }

    public record Result(Level level, List<String> reasons) {

        public boolean safe() {
            return level == Level.SAFE;
        }
    }

    /** Zip içinde en fazla bu kadar girdiye bakılır (aşırı büyük arşivle uğraşmamak için). */
    private static final int MAX_ZIP_ENTRIES = 2000;

    private FileRisk() {
    }

    public static Result analyze(String fileName, byte[] data) {
        String name = fileName == null ? "" : fileName;
        List<String> blocked = new ArrayList<>();
        List<String> risky = new ArrayList<>();

        if (name.indexOf('‮') >= 0 || name.indexOf('‭') >= 0) {
            blocked.add("Dosya adında yönü ters çeviren gizli karakter var (uzantıyı gizlemek için kullanılır).");
        }
        String ext = FileTypes.extension(name);
        if (FileTypes.EXECUTABLE.contains(ext)) {
            blocked.add("Çalıştırılabilir dosya ya da betik (." + ext + ").");
        }
        if (isExecutable(data)) {
            blocked.add("Dosyanın içeriği çalıştırılabilir bir program.");
        }
        if (!blocked.isEmpty()) {
            return new Result(Level.BLOCKED, blocked);
        }

        if (hiddenExtension(name)) {
            risky.add("Dosya adı iki uzantılı; asıl türü gizleniyor olabilir.");
        }
        String mismatch = mismatch(ext, data);
        if (mismatch != null) {
            risky.add(mismatch);
        }
        switch (ext) {
            case "docm", "xlsm", "pptm" -> risky.add("Makro içerebilen Office dosyası (." + ext + ").");
            case "docx", "xlsx", "pptx" -> {
                if (zipContains(data, entry -> entry.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"))) {
                    risky.add("Office dosyası makro içeriyor.");
                }
            }
            case "doc", "xls", "ppt" -> {
                if (contains(data, "_VBA_PROJECT".getBytes(StandardCharsets.UTF_16LE))) {
                    risky.add("Eski biçim Office dosyası makro içeriyor.");
                }
            }
            case "pdf" -> {
                String text = new String(data, StandardCharsets.ISO_8859_1);
                if (text.contains("/JavaScript") || text.contains("/JS")) {
                    risky.add("PDF betik (JavaScript) içeriyor.");
                }
                if (text.contains("/Launch")) {
                    risky.add("PDF açılınca program başlatmaya çalışabilir (/Launch).");
                }
                if (text.contains("/EmbeddedFile")) {
                    risky.add("PDF içine gömülü dosya var.");
                }
            }
            case "zip" -> {
                List<String> inside = zipExecutables(data);
                if (!inside.isEmpty()) {
                    risky.add("Arşivin içinde program ya da betik var: " + String.join(", ", inside) + ".");
                }
            }
            case "7z", "rar" -> risky.add("Arşivin içeriği denetlenemedi (." + ext + "); içinden çıkan dosyalara dikkat edin.");
            case "svg" -> {
                String text = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (text.contains("<script") || text.matches("(?s).*\\son[a-z]+\\s*=.*")) {
                    risky.add("SVG resim betik içeriyor.");
                }
            }
            default -> {
                if (FileTypes.mimeType(name) == null) {
                    risky.add("Bilinmeyen dosya türü (." + (ext.isEmpty() ? "uzantısız" : ext) + ").");
                }
            }
        }
        return new Result(risky.isEmpty() ? Level.SAFE : Level.RISKY, risky);
    }

    /**
     * Windows (MZ + PE başlığı), Linux (ELF), macOS (Mach-O) programı, Java sınıfı ya da "#!/" ile başlayan betik.
     * "MZ" ile başlayan sıradan bir metin program sayılmaz: PE başlığı da aranır.
     */
    static boolean isExecutable(byte[] d) {
        return isWindowsProgram(d)
                || startsWith(d, 0x7F, 'E', 'L', 'F')
                || startsWith(d, 0xCF, 0xFA, 0xED, 0xFE) || startsWith(d, 0xCE, 0xFA, 0xED, 0xFE)
                || startsWith(d, 0xFE, 0xED, 0xFA, 0xCE) || startsWith(d, 0xFE, 0xED, 0xFA, 0xCF)
                || startsWith(d, 0xCA, 0xFE, 0xBA, 0xBE)
                || startsWith(d, '#', '!', '/');
    }

    private static boolean isWindowsProgram(byte[] d) {
        if (!startsWith(d, 'M', 'Z') || d.length < 0x40) {
            return false;
        }
        int pe = (d[0x3C] & 0xFF) | (d[0x3D] & 0xFF) << 8 | (d[0x3E] & 0xFF) << 16 | (d[0x3F] & 0xFF) << 24;
        return pe > 0 && pe + 4 <= d.length && d[pe] == 'P' && d[pe + 1] == 'E' && d[pe + 2] == 0 && d[pe + 3] == 0;
    }

    /** "fatura.pdf.zip" gibi: sondan bir önceki parça bilinen bir belge türüyse asıl tür gizleniyor olabilir. */
    private static boolean hiddenExtension(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("\\.");
        if (parts.length < 3) {
            return false;
        }
        String previous = parts[parts.length - 2].trim();
        return FileTypes.mimeType("x." + previous) != null && !previous.equals(parts[parts.length - 1].trim());
    }

    /** Uzantının beklediği imza ile içerik uyuşmuyor mu? Sadece imzası belli türlerde bakılır. */
    static String mismatch(String ext, byte[] d) {
        boolean ok = switch (ext) {
            case "pdf" -> startsWith(d, '%', 'P', 'D', 'F');
            case "png" -> startsWith(d, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(d, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(d, 'G', 'I', 'F', '8');
            case "bmp" -> startsWith(d, 'B', 'M');
            case "docx", "xlsx", "pptx", "docm", "xlsm", "pptm", "odt", "ods", "odp", "zip" -> startsWith(d, 'P', 'K');
            case "doc", "xls", "ppt" -> startsWith(d, 0xD0, 0xCF, 0x11, 0xE0);
            case "7z" -> startsWith(d, '7', 'z', 0xBC, 0xAF);
            case "rar" -> startsWith(d, 'R', 'a', 'r', '!');
            default -> true;
        };
        return ok ? null : "Dosyanın içeriği uzantısıyla (." + ext + ") uyuşmuyor.";
    }

    private static List<String> zipExecutables(byte[] data) {
        List<String> found = new ArrayList<>();
        zipContains(data, entry -> {
            String base = entry.substring(entry.lastIndexOf('/') + 1);
            if (FileTypes.EXECUTABLE.contains(FileTypes.extension(base)) && found.size() < 5) {
                found.add(base);
            }
            return false;
        });
        return found;
    }

    private static boolean zipContains(byte[] data, java.util.function.Predicate<String> test) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            for (int i = 0; i < MAX_ZIP_ENTRIES && (entry = zip.getNextEntry()) != null; i++) {
                if (test.test(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // Bozuk ya da şifreli arşiv: içeriğe bakılamaz; imza uyuşmazlığı ayrıca yakalanır.
        }
        return false;
    }

    private static boolean startsWith(byte[] d, int... prefix) {
        if (d == null || d.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((d[i] & 0xFF) != (prefix[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
