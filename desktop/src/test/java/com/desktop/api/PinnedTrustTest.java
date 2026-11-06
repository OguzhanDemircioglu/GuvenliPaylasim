package com.desktop.api;

import com.desktop.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Security.md S1: sabitlenen değerler gerçekten Let's Encrypt köklerinin anahtarı; başka bir kök eşleşmez. */
class PinnedTrustTest {

    private static final String ISRG_ROOT_X1 = "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=";
    private static final String ISRG_ROOT_X2 = "diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=";

    @Test
    void configuredPinsIncludeTheLetsEncryptRootsFromTheJdkTrustStore() throws Exception {
        Set<String> configured = AppConfig.get().serverPins();
        List<X509Certificate> isrg = roots().stream()
                .filter(c -> c.getSubjectX500Principal().getName().contains("ISRG Root X")).toList();

        assertEquals(2, isrg.size(), "JDK güven deposunda ISRG Root X1 ve X2 olmalı");
        for (X509Certificate root : isrg) {
            assertTrue(configured.contains(PinnedTrust.pin(root)), root.getSubjectX500Principal().getName());
        }
        assertTrue(configured.containsAll(Set.of(ISRG_ROOT_X1, ISRG_ROOT_X2)));
    }

    @Test
    void aPathThroughAnotherCertificateAuthorityDoesNotMatch() throws Exception {
        Set<String> pins = AppConfig.get().serverPins();
        List<X509Certificate> others = roots().stream()
                .filter(c -> !c.getSubjectX500Principal().getName().contains("ISRG")).toList();

        assertFalse(others.isEmpty());
        for (X509Certificate other : others) {
            assertFalse(PinnedTrust.matches(List.of(other), pins), other.getSubjectX500Principal().getName());
        }
        X509Certificate x1 = roots().stream().filter(c -> PinnedTrust.pin(c).equals(ISRG_ROOT_X1)).findFirst().orElseThrow();
        assertTrue(PinnedTrust.matches(List.of(others.getFirst(), x1), pins));
    }

    @Test
    void pinListParsing() {
        assertEquals(Set.of("a", "b"), PinnedTrust.parse(" a, b ,,"));
        assertTrue(PinnedTrust.parse("  ").isEmpty());
        assertTrue(PinnedTrust.parse(null).isEmpty());
    }

    private static List<X509Certificate> roots() throws Exception {
        KeyStore store = PinnedTrust.systemTrustStore();
        List<X509Certificate> certificates = new ArrayList<>();
        for (String alias : Collections.list(store.aliases())) {
            if (store.getCertificate(alias) instanceof X509Certificate certificate) {
                certificates.add(certificate);
            }
        }
        return certificates;
    }
}
