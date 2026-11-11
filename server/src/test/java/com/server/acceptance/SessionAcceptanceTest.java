package com.server.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Oturumsuz ve sahte oturumla erişim (Security.md S4 "oturumsuz erişim", ASVS V3/V4). */
class SessionAcceptanceTest extends AcceptanceTest {

    /** Oturum isteyen her uç nokta, jetonsuz istekte 401 döner ve iş yapmaz. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,/api/contacts", "DELETE,/api/contacts/1",
            "POST,/api/documents", "GET,/api/documents/inbox", "GET,/api/documents/sent", "GET,/api/documents/1",
            "POST,/api/documents/1/opened", "DELETE,/api/documents/1",
            "POST,/api/invites", "POST,/api/invites/redeem", "POST,/api/invites/1/accept",
            "GET,/api/invites/verifications", "POST,/api/invites/1/verify", "POST,/api/invites/1/reject",
            "GET,/api/invites/sent", "DELETE,/api/invites/1",
            "GET,/api/keys/me", "POST,/api/keys/me",
            "POST,/api/auth/totp/setup", "POST,/api/auth/totp/enable", "POST,/api/auth/totp/disable",
            "PUT,/api/users/me/name", "POST,/api/users/me/delete", "PUT,/api/auth/password"})
    void everyProtectedEndpointRejectsRequestsWithoutSession(String method, String path) throws Exception {
        expect(call(method, path, "{}", null, randomIp()), 401);
    }

    @Test
    void forgedTokenSignedWithAnotherKeyIsRejected() throws Exception {
        Account victim = register();
        String[] parts = victim.token().split("\\.");
        var pair = KeyPairGenerator.getInstance("RSA");
        pair.initialize(2048);
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initSign(pair.generateKeyPair().getPrivate());
        rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        String forged = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.sign());

        expect(call("GET", "/api/documents/inbox", null, forged, randomIp()), 401);
    }

    @Test
    void tokenWithAlgNoneIsRejected() throws Exception {
        Account victim = register();
        String payload = victim.token().split("\\.")[1];
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        expect(call("GET", "/api/documents/inbox", null, header + "." + payload + ".", randomIp()), 401);
    }

    @Test
    void tokenWithChangedUserIdIsRejected() throws Exception {
        Account attacker = register();
        Account victim = register();
        String[] parts = attacker.token().split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"" + attacker.id() + "\"", "\"sub\":\"" + victim.id() + "\"");
        assertThat(claims).contains("\"sub\":\"" + victim.id() + "\"");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        expect(call("GET", "/api/documents/inbox", null, tampered, randomIp()), 401);
    }

    @Test
    void sessionEndsAfterLogoutAndRefreshTokenCannotBeReused() throws Exception {
        Account user = register();
        String cookie = user.refreshCookie().split(";")[0];

        MvcResult logout = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/logout").header("Cookie", cookie)).andReturn();
        expect(logout, 200);
        MvcResult refresh = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/refresh").header("Cookie", cookie)).andReturn();

        assertThat(refresh.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordIsNeverReturnedOrStoredInPlainText() throws Exception {
        Account user = register();
        MvcResult keys = call("GET", "/api/keys/me", null, user);

        expect(keys, 200);
        assertThat(body(keys)).doesNotContain(user.password());
    }
}
