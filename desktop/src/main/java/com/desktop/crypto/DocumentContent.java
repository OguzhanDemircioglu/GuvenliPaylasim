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
 */
public record DocumentContent(boolean text, String fileName, String mimeType, byte[] data) {

    private static final byte VERSION = 1;

    public static DocumentContent ofText(String text) {
        return new DocumentContent(true, null, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }

    public static DocumentContent ofFile(String fileName, String mimeType, byte[] data) {
        return new DocumentContent(false, fileName, mimeType, data);
    }

    public String asText() {
        return new String(data, StandardCharsets.UTF_8);
    }

    byte[] serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length + 128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeBoolean(text);
            out.writeUTF(fileName == null ? "" : fileName);
            out.writeUTF(mimeType == null ? "" : mimeType);
            out.writeInt(data.length);
            out.write(data);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new CryptoException("Belge hazırlanamadı", e);
        }
    }

    static DocumentContent deserialize(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readByte() != VERSION) {
                throw new CryptoException("Belge biçimi tanınmıyor");
            }
            boolean text = in.readBoolean();
            String fileName = in.readUTF();
            String mimeType = in.readUTF();
            byte[] data = in.readNBytes(in.readInt());
            return new DocumentContent(text, fileName.isEmpty() ? null : fileName, mimeType, data);
        } catch (IOException e) {
            throw new CryptoException("Belge bozuk", e);
        }
    }
}
