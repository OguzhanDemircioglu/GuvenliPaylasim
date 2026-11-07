package com.desktop.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Kaydedilen dosyaya Windows'un dış kaynak etiketi (Mark of the Web, {@code Zone.Identifier}, ZoneId=3) konur:
 * tarayıcı ya da e-postayla inen dosyalara Windows'un kendisinin koyduğu etiket. Uygulama dosyayı kendisi yazdığı için
 * etiket kendiliğinden konmaz. Etiketle SmartScreen ve antivirüs dosyaya dikkatli davranır; Office makroları varsayılan
 * olarak çalıştırmaz ve dosyayı Korumalı Görünüm'de açar.
 */
public final class WebMark {

    private static final String CONTENT = "[ZoneTransfer]\r\nZoneId=3\r\n";

    private WebMark() {
    }

    /**
     * İşareti yazar; dosya sistemi desteklemiyorsa (ör. FAT biçimli USB bellek) false döner ve dosya işaretsiz kalır.
     * {@link java.nio.file.Path} iki nokta içeren adı kabul etmediği için {@link File} ile yazılır (NTFS alternatif veri
     * akışı).
     */
    public static boolean apply(File file) {
        try (FileOutputStream out = new FileOutputStream(file.getPath() + ":Zone.Identifier")) {
            out.write(CONTENT.getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
