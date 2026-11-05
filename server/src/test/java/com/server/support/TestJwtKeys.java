package com.server.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Testler için çalışma anında RSA anahtar çifti üretir (repoda private key tutulmasın diye).
 */
public final class TestJwtKeys {

    private static final Path DIRECTORY = generate();

    private TestJwtKeys() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.key-id", () -> "test-key");
        registry.add("app.jwt.private-key-path", () -> DIRECTORY.resolve("private.pem").toString());
        registry.add("app.jwt.public-key-path", () -> DIRECTORY.resolve("public.pem").toString());
    }

    private static Path generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            Path directory = Files.createTempDirectory("jwt-test-keys");
            directory.toFile().deleteOnExit();
            write(directory.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
            write(directory.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
            return directory;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        file.toFile().deleteOnExit();
    }
}
