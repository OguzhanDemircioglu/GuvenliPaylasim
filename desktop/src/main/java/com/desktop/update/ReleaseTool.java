package com.desktop.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sürüm yayımlayanın aracı (docs/Update_Design.md). Uygulamanın parçası değildir; build makinesinde çalıştırılır.
 * <pre>
 * keygen &lt;klasör&gt;                                  imza anahtar çiftini üretir
 * sign &lt;kurulum dosyası&gt; &lt;sürüm&gt; &lt;özel anahtar&gt; [--mandatory]   release.json üretir
 * </pre>
 * Özel anahtar repoya konmaz. Açık anahtar src/main/resources/com/desktop/update-public-key.txt dosyasına yazılır.
 */
public final class ReleaseTool {

    private ReleaseTool() {
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length >= 2 && args[0].equals("keygen")) {
            keygen(Path.of(args[1]));
        } else if (args.length >= 4 && args[0].equals("sign")) {
            sign(Path.of(args[1]), args[2], Path.of(args[3]), args.length > 4 && args[4].equals("--mandatory"));
        } else {
            System.err.println("Kullanım: keygen <klasör> | sign <kurulum> <sürüm> <özel-anahtar> [--mandatory]");
            System.exit(2);
        }
    }

    private static void keygen(Path dir) throws IOException, GeneralSecurityException {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(dir);
        Path privateKey = dir.resolve("update-private-key.txt");
        Path publicKey = dir.resolve("update-public-key.txt");
        Files.writeString(privateKey, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(publicKey, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        System.out.println("Özel anahtar (gizli tutun, repoya koymayın): " + privateKey);
        System.out.println("Açık anahtar (uygulamaya gömülür): " + publicKey);
    }

    private static void sign(Path installer, String version, Path privateKeyFile, boolean mandatory)
            throws IOException, GeneralSecurityException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(installer)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                sha.update(buffer, 0, n);
            }
        }
        String sha256 = HexFormat.of().formatHex(sha.digest());
        String signature = ReleaseSignature.sign(ReleaseSignature.privateKey(Files.readString(privateKeyFile)),
                version, sha256, mandatory);
        String json = """
                {
                  "version": "%s",
                  "installer": "%s",
                  "sha256": "%s",
                  "mandatory": %s,
                  "signature": "%s"
                }
                """.formatted(Version.parse(version), installer.getFileName(), sha256, mandatory, signature);
        Path out = installer.resolveSibling("release.json");
        Files.writeString(out, json, StandardCharsets.UTF_8);
        System.out.println("Yazıldı: " + out);
    }
}
