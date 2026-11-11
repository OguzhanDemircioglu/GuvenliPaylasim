package com.server.acceptance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bozuk ve kötü niyetli girdiler (ASVS V5, V13): istek reddedilir, kayıt oluşmaz, hata cevabı iç ayrıntı sızdırmaz.
 */
class InputAcceptanceTest extends AcceptanceTest {

    @Test
    void malformedJsonIsRejectedWithoutInternalDetails() throws Exception {
        Account user = register();
        MvcResult result = call("POST", "/api/documents", "{\"kind\":", user);

        expect(result, 400);
        assertNoInternals(body(result));
    }

    @Test
    void callingTheErrorPageDirectlyIsANotFoundNotAServerError() throws Exception {
        for (String method : new String[]{"GET", "POST", "DELETE", "PATCH"}) {
            expect(call(method, "/error", null, null, randomIp()), 404);
        }
    }

    @Test
    void responsesForbidCrossOriginLoading() throws Exception {
        Account user = register();
        MvcResult result = call("GET", "/api/keys/me", null, user);

        assertThat(result.getResponse().getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void nonNumericIdIsRejected() throws Exception {
        Account user = register();
        MvcResult result = call("GET", "/api/documents/abc", null, user);

        expect(result, 400);
        assertNoInternals(body(result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 OR 1=1", "1;DROP TABLE users", "-1", "0", "99999999999"})
    void injectedOrUnknownIdsBehaveAsMissing(String id) throws Exception {
        Account user = register();
        int status = call("GET", "/api/documents/" + id, null, user).getResponse().getStatus();

        assertThat(status).isIn(400, 404);
        expect(call("GET", "/api/documents/inbox", null, user), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script> Ali", "Ali'; DROP TABLE users; --", "Ali\u0000 Veli", "Ali"})
    void invalidNamesAreRejected(String name) throws Exception {
        Account user = register();
        MvcResult result = call("PUT", "/api/users/me/name", "{\"fullName\":\"" + name.replace("\u0000", "\\u0000")
                + "\"}", user);

        assertThat(result.getResponse().getStatus()).isIn(400, 422);
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1", "5551234567' --", "+905551234567", "05551234567", "1234"})
    void malformedPhonesAreRejectedAtLogin(String phone) throws Exception {
        MvcResult result = login(phone, "anything-long-enough", randomIp());

        expect(result, 400);
    }

    @Test
    void sendingToYourselfIsRejected() throws Exception {
        Account user = register();
        MvcResult result = call("POST", "/api/documents", box(user.id(), user.id() + 0), user);

        expect(result, 422);
    }

    @Test
    void duplicateRecipientsAreRejected() throws Exception {
        Account sender = register();
        Account recipient = register();
        connect(sender, recipient);
        MvcResult result = call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[{"userId":%1$d,"wrappedKey":"wk"},{"userId":%1$d,"wrappedKey":"wk"}]}"""
                .formatted(recipient.id()), sender);

        expect(result, 422);
    }

    @Test
    void invalidBase64PayloadAndUnknownLifetimeAreRejected() throws Exception {
        Account sender = register();
        Account recipient = register();
        connect(sender, recipient);

        expect(call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"%%%%not-base64","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[{"userId":%d,"wrappedKey":"wk"}]}""".formatted(recipient.id()), sender), 422);
        expect(call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig","lifetimeHours":100000,"deleteOnOpen":false,
                 "recipients":[{"userId":%d,"wrappedKey":"wk"}]}""".formatted(recipient.id()), sender), 422);
        assertThat(JsonPath.<java.util.List<?>>read(body(call("GET", "/api/documents/inbox", null, recipient)),
                "$.data")).isEmpty();
    }

    @Test
    void bodyLargerThanTheLimitIsRejected() throws Exception {
        Account user = register();
        String huge = "{\"kind\":\"TEXT\",\"payload\":\"" + "A".repeat(17 * 1024 * 1024) + "\"}";

        int status = call("POST", "/api/documents", huge, user).getResponse().getStatus();

        assertThat(status).isIn(400, 413);
    }

    @Test
    void registrationWithInvalidPublicKeyIsRejectedAndCreatesNoAccount() throws Exception {
        String phone = freshPhone();
        String ip = randomIp();
        String code = JsonPath.read(body(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}",
                null, ip)), "$.data.testCode");

        MvcResult result = call("POST", "/api/auth/register/complete", """
                {"phone":"%s","code":"%s","fullName":"Test Kullanıcı","password":"long-enough-secret",
                 "keys":{"encryptionPublicKey":"bm90LWEta2V5","signingPublicKey":"bm90LWEta2V5",
                 "privateKeyBundle":"b"}}""".formatted(phone, code), null, ip);

        expect(result, 422);
        expect(login(phone, "long-enough-secret", randomIp()), 401);
    }

    @Test
    void registrationCodeOfAnotherNumberDoesNotWork() throws Exception {
        String ip = randomIp();
        String phoneA = freshPhone();
        String phoneB = freshPhone();
        String codeA = JsonPath.read(body(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phoneA + "\"}",
                null, ip)), "$.data.testCode");
        call("POST", "/api/auth/register/start", "{\"phone\":\"" + phoneB + "\"}", null, ip);

        MvcResult result = call("POST", "/api/auth/register/complete", """
                {"phone":"%s","code":"%s","fullName":"Test Kullanıcı","password":"long-enough-secret","keys":%s}"""
                .formatted(phoneB, codeA, validKeysJson()), null, ip);

        expect(result, 422);
        expect(login(phoneB, "long-enough-secret", randomIp()), 401);
    }

    @Test
    void alreadyRegisteredNumberCannotRegisterAgain() throws Exception {
        Account existing = register();
        String phone = existing.phone().substring(3);
        String ip = randomIp();
        String code = JsonPath.read(body(call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}",
                null, ip)), "$.data.testCode");

        MvcResult result = call("POST", "/api/auth/register/complete", """
                {"phone":"%s","code":"%s","fullName":"Saldırgan Kişi","password":"attacker-secret-1",
                 "keys":{"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"b"}}"""
                .formatted(phone, code, existing.encryptionPublicKey(), existing.signingPublicKey()), null, ip);

        assertThat(result.getResponse().getStatus()).isIn(409, 422);
        expect(login(phone, existing.password(), randomIp()), 200);
        expect(login(phone, "attacker-secret-1", randomIp()), 401);
    }

    private static String box(Long... recipients) {
        StringBuilder list = new StringBuilder();
        for (Long id : recipients) {
            list.append(list.isEmpty() ? "" : ",").append("{\"userId\":").append(id).append(",\"wrappedKey\":\"wk\"}");
        }
        return """
                {"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[%s]}""".formatted(list);
    }

    private static void assertNoInternals(String body) {
        assertThat(body).doesNotContainIgnoringCase("exception").doesNotContain("at com.").doesNotContain("org.")
                .doesNotContainIgnoringCase("sql");
    }
}
