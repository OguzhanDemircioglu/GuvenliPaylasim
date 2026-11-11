package com.server.acceptance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deneme ve hız sınırları (Security.md S4, S8; ASVS V2.2). Sınır aşılınca doğru bilgiyle bile reddedilir; IP
 * değiştirmek numara başına sınırı atlatmaz.
 */
class RateLimitAcceptanceTest extends AcceptanceTest {

    @Test
    void loginIsBlockedAfterFiveWrongPasswordsEvenWithTheRightOne() throws Exception {
        Account user = register();
        String phone = user.phone().substring(3);
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            expect(login(phone, "wrong-password-" + i, ip), 401);
        }

        expect(login(phone, user.password(), ip), 429);
    }

    @Test
    void changingIpDoesNotBypassThePerNumberLoginLimit() throws Exception {
        Account user = register();
        String phone = user.phone().substring(3);
        for (int i = 0; i < 30; i++) {
            expect(login(phone, "wrong-password-" + i, randomIp()), 401);
        }

        expect(login(phone, user.password(), randomIp()), 429);
    }

    @Test
    void unknownAndKnownNumbersGetTheSameLoginAnswer() throws Exception {
        Account user = register();
        String known = body(login(user.phone().substring(3), "wrong-password", randomIp()));
        String unknown = body(login(freshPhone(), "wrong-password", randomIp()));

        assertThat(JsonPath.<String>read(known, "$.message")).isEqualTo(JsonPath.read(unknown, "$.message"));
    }

    @Test
    void passwordChangeIsBlockedAfterFiveWrongCurrentPasswords() throws Exception {
        Account user = register();
        for (int i = 0; i < 5; i++) {
            expect(call("PUT", "/api/auth/password", change("wrong-current-" + i, user), user), 422);
        }

        expect(call("PUT", "/api/auth/password", change(user.password(), user), user), 429);
        expect(login(user.phone().substring(3), user.password(), randomIp()), 200);
    }

    @Test
    void accountDeletionIsBlockedAfterFiveWrongPasswords() throws Exception {
        Account user = register();
        for (int i = 0; i < 5; i++) {
            expect(call("POST", "/api/users/me/delete", "{\"password\":\"wrong-" + i + "\"}", user), 422);
        }

        expect(call("POST", "/api/users/me/delete", "{\"password\":\"" + user.password() + "\"}", user), 429);
        expect(login(user.phone().substring(3), user.password(), randomIp()), 200);
    }

    @Test
    void verificationCodesAreLimitedPerNumberAcrossIps() throws Exception {
        String phone = freshPhone();
        for (int i = 0; i < 3; i++) {
            expect(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}", null, randomIp()), 200);
        }

        expect(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}", null, randomIp()), 429);
        expect(call("POST", "/api/auth/password-reset/start", "{\"phone\":\"" + phone + "\"}", null, randomIp()),
                429);
    }

    @Test
    void verificationCodesAreLimitedPerIp() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 10; i++) {
            expect(call("POST", "/api/auth/register/start", "{\"phone\":\"" + freshPhone() + "\"}", null, ip), 200);
        }

        expect(call("POST", "/api/auth/register/start", "{\"phone\":\"" + freshPhone() + "\"}", null, ip), 429);
    }

    @Test
    void verificationCodeIsDestroyedAfterThreeWrongGuesses() throws Exception {
        String phone = freshPhone();
        String ip = randomIp();
        String code = JsonPath.read(body(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}",
                null, ip)), "$.data.testCode");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) {
            expect(register(phone, wrong, ip), 422);
        }

        expect(register(phone, code, ip), 422);
        expect(login(phone, "long-enough-secret", randomIp()), 401);
    }

    @Test
    void inviteCodeGuessingIsBlocked() throws Exception {
        Account user = register();
        for (int i = 0; i < 5; i++) {
            String verifier = Base64.getEncoder().encodeToString(randomHex(16).getBytes());
            expect(call("POST", "/api/invites/redeem", "{\"verifier\":\"" + verifier + "\"}", user), 422);
        }

        MvcResult blocked = call("POST", "/api/invites/redeem",
                "{\"verifier\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}", user);
        expect(blocked, 429);
    }

    private MvcResult register(String phone, String code, String ip) throws Exception {
        return call("POST", "/api/auth/register/complete", """
                {"phone":"%s","code":"%s","fullName":"Test Kullanıcı","password":"long-enough-secret","keys":%s}"""
                .formatted(phone, code, validKeysJson()), null, ip);
    }

    private static String change(String current, Account user) {
        return """
                {"currentPassword":"%s","newPassword":"%s-new",
                 "keys":{"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"b"}}"""
                .formatted(current, user.password(), user.encryptionPublicKey(), user.signingPublicKey());
    }
}
