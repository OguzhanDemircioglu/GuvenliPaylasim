package com.desktop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dosya riski içeriğe bakarak belirlenir: program engellenir, makro/betik/şüpheli arşiv alıcıya uyarı verir. */
class FileRiskTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Test
    void ordinaryFilesAreSafe() throws Exception {
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("rapor.pdf", PDF).level());
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("foto.jpg", JPG).level());
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("ekran.PNG", PNG).level());
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("not.txt", "MZ Holding toplantı notu".getBytes()).level(),
                "MZ ile başlayan metin program sayılmaz");
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("tablo.xlsx", zip("xl/workbook.xml")).level());
        assertEquals(FileRisk.Level.SAFE, FileRisk.analyze("veri.csv", "a;b\n1;2".getBytes()).level());
    }

    @Test
    void programsAreBlockedWhateverTheExtension() throws Exception {
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("kur.exe", PDF).level());
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("fatura.pdf.exe", PDF).level());
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("betik.ps1", "Write-Host".getBytes()).level());
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("resim.jpg", windowsProgram()).level(),
                "uzantısı değiştirilmiş program");
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("not.txt", "#!/bin/sh\nrm -rf ~".getBytes()).level());
        assertEquals(FileRisk.Level.BLOCKED, FileRisk.analyze("fatura‮fdp.exe", PDF).level(),
                "yönü ters çeviren karakter");
    }

    @Test
    void macrosScriptsAndArchivesNeedAWarning() throws Exception {
        assertRisky("tablo.xlsm", zip("xl/workbook.xml"), "Makro");
        assertRisky("tablo.xlsx", zip("xl/workbook.xml", "xl/vbaProject.bin"), "makro");
        assertRisky("eski.doc", ole("_VBA_PROJECT"), "makro");
        assertRisky("form.pdf", "%PDF-1.4 /OpenAction << /S /JavaScript /JS (app.alert(1)) >>".getBytes(), "JavaScript");
        assertRisky("ek.pdf", "%PDF-1.4 /Launch /F (cmd.exe)".getBytes(), "/Launch");
        assertRisky("paket.zip", zip("belgeler/rapor.pdf", "belgeler/kur.exe"), "kur.exe");
        assertRisky("logo.svg", "<svg onload=\"alert(1)\"></svg>".getBytes(), "SVG");
        assertRisky("arsiv.rar", new byte[]{'R', 'a', 'r', '!', 0x1A, 7}, "denetlenemedi");
        assertRisky("foto.jpg", PDF, "uyuşmuyor");
        assertRisky("fatura.pdf.zip", zip("fatura.pdf"), "iki uzantılı");
        assertRisky("garip.xyz", new byte[]{1, 2, 3}, "Bilinmeyen");
    }

    @Test
    void allowedListCoversCommonFilesButNotPrograms() {
        for (String name : new String[]{"a.png", "a.gif", "a.webp", "a.heic", "a.xlsx", "a.xls", "a.txt", "a.csv",
                "a.pptx", "a.odt", "a.zip", "a.mp4", "a.docx", "a.pdf", "a.jpeg"}) {
            assertNotNull(FileTypes.mimeType(name), name);
        }
        for (String name : new String[]{"a.exe", "a.bat", "a.js", "a.html", "a.lnk", "a.msi", "a"}) {
            assertNull(FileTypes.mimeType(name), name);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void savedFilesGetTheInternetZoneMark(@TempDir Path dir) throws Exception {
        File file = dir.resolve("rapor.pdf").toFile();
        Files.write(file.toPath(), PDF);

        assertTrue(WebMark.apply(file));

        try (FileInputStream in = new FileInputStream(file.getPath() + ":Zone.Identifier")) {
            String mark = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(mark.contains("ZoneId=3"), mark);
        }
        assertEquals(PDF.length, Files.size(file.toPath()), "dosyanın kendisi değişmez");
    }

    private static void assertRisky(String name, byte[] data, String reasonPart) {
        FileRisk.Result result = FileRisk.analyze(name, data);
        assertEquals(FileRisk.Level.RISKY, result.level(), name + " " + result.reasons());
        assertTrue(result.reasons().stream().anyMatch(r -> r.contains(reasonPart)), name + " " + result.reasons());
    }

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write("x".getBytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Eski Office (OLE) imzası + UTF-16 akış adı. */
    private static byte[] ole(String stream) {
        byte[] header = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        byte[] name = stream.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[header.length + 32 + name.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(name, 0, out, header.length + 32, name.length);
        return out;
    }

    /** MZ + 0x3C'de PE başlığının yeri + "PE\0\0". */
    private static byte[] windowsProgram() {
        byte[] d = new byte[0x90];
        d[0] = 'M';
        d[1] = 'Z';
        d[0x3C] = (byte) 0x80;
        d[0x80] = 'P';
        d[0x81] = 'E';
        return d;
    }
}
