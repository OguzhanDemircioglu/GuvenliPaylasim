package com.desktop.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Belgenin şifrelenmeden önceki hali. Dosya adı ve türü de şifreli içeriğin içindedir; sunucu bunları bilmez.
 *
 * @param fileName düz yazıda null
 * @param mimeType düz yazıda "text/plain"
 * @param viewOnly gönderen kaydetmeye izin vermedi: alıcı sadece uygulama içinde görüntüler (Crypto_Design §8.3.1).
 *                 Bayrak şifreli ve imzalı içeriğin içindedir; sunucu ne görebilir ne kaldırabilir. Alıcının
 *                 uygulaması uygular, şifreleme garantisi değildir.
 */
public record DocumentContent(boolean text, String fileName, String mimeType, byte[] data, boolean viewOnly) {

    /** v1: bayraksız. v2: {@code viewOnly} ekli; sadece kaydedilemez belgelerde yazılır, eski sürümler v2'yi açmaz. */
    private static final byte VERSION_1 = 1;
    private static final byte VERSION_2 = 2;

    public static DocumentContent ofText(String text) {
        return new DocumentContent(true, null, "text/plain", text.getBytes(StandardCharsets.UTF_8), false);
    }

    public static DocumentContent ofFile(String fileName, String mimeType, byte[] data) {
        return new DocumentContent(false, fileName, mimeType, data, false);
    }

    public DocumentContent asViewOnly() {
        return new DocumentContent(text, fileName, mimeType, data, true);
    }

    public String asText() {
        return new String(data, StandardCharsets.UTF_8);
    }

    byte[] serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length + 128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(viewOnly ? VERSION_2 : VERSION_1);
            out.writeBoolean(text);
            out.writeUTF(fileName == null ? "" : fileName);
            out.writeUTF(mimeType == null ? "" : mimeType);
            if (viewOnly) {
                out.writeBoolean(true);
            }
            out.writeInt(data.length);
            out.write(data);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new CryptoException("Belge hazırlanamadı", e);
        }
    }

    static DocumentContent deserialize(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte version = in.readByte();
            if (version != VERSION_1 && version != VERSION_2) {
                throw new CryptoException("Belge biçimi tanınmıyor");
            }
            boolean text = in.readBoolean();
            String fileName = in.readUTF();
            String mimeType = in.readUTF();
            boolean viewOnly = version == VERSION_2 && in.readBoolean();
            byte[] data = in.readNBytes(in.readInt());
            return new DocumentContent(text, fileName.isEmpty() ? null : fileName, mimeType, data, viewOnly);
        } catch (IOException e) {
            throw new CryptoException("Belge bozuk", e);
        }
    }
}
