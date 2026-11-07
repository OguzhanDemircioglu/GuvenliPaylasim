package com.desktop.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gönderilebilecek dosya türleri (docs/UI_Standards.md §4.5). Çalıştırılabilir dosyalar ve betikler listede yoktur;
 * {@link FileRisk} ayrıca içeriğe bakar (uzantısı değiştirilmiş program da engellenir).
 */
public final class FileTypes {

    /** Uzantı → MIME türü. */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            // Resimler
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("bmp", "image/bmp"), Map.entry("webp", "image/webp"),
            Map.entry("tif", "image/tiff"), Map.entry("tiff", "image/tiff"), Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"), Map.entry("svg", "image/svg+xml"), Map.entry("ico", "image/x-icon"),
            // Belgeler
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"),
            Map.entry("rtf", "application/rtf"), Map.entry("md", "text/markdown"), Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            // Office ve OpenDocument (makrolu türler alıcıda uyarı verir)
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("docm", "application/vnd.ms-word.document.macroEnabled.12"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),
            // Arşivler (içinde program varsa alıcıda uyarı verir)
            Map.entry("zip", "application/zip"), Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar"),
            // Ses ve görüntü
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("m4a", "audio/mp4"),
            Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"));

    /** Çalıştırılabilir dosya ve betik uzantıları: gönderilemez; arşivin içinde ya da çift uzantıda da aranır. */
    static final Set<String> EXECUTABLE = Set.of(
            "exe", "msi", "msp", "com", "scr", "pif", "bat", "cmd", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf",
            "wsh", "hta", "lnk", "url", "jar", "dll", "sys", "cpl", "reg", "inf", "scf", "msc", "gadget", "appx",
            "msix", "iso", "img", "vhd", "vhdx", "apk", "app", "sh", "py", "pyw", "html", "htm", "xhtml", "chm");

    private FileTypes() {
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** İzin verilen türün MIME karşılığı; izin verilmiyorsa null. */
    public static String mimeType(String fileName) {
        return ALLOWED.get(extension(fileName));
    }

    public static Set<String> allowedExtensions() {
        return ALLOWED.keySet();
    }
}
