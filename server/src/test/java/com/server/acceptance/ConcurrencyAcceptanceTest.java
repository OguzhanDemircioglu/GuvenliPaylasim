package com.server.acceptance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aynı anda gelen istekler (Security.md S4 "paralel isteklerle atlatılamaz"): sınırlar ve tek seferlik işlemler
 * eş zamanlı isteklerle aşılamaz.
 */
class ConcurrencyAcceptanceTest extends AcceptanceTest {

    private static final int PARALLEL = 12;

    @Test
    void parallelCodeRequestsCannotExceedThePerNumberLimit() throws Exception {
        String phone = freshPhone();

        List<Integer> statuses = parallel(() -> call("POST", "/api/auth/register/start",
                "{\"phone\":\"" + phone + "\"}", null, randomIp()).getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses).isSubsetOf(200, 429);
    }

    @Test
    void parallelWrongPasswordsStillBlockTheLogin() throws Exception {
        Account user = register();
        String phone = user.phone().substring(3);
        String ip = randomIp();

        List<Integer> statuses = parallel(() -> login(phone, "wrong-" + Thread.currentThread().threadId(), ip)
                .getResponse().getStatus());

        // Şifresi gerçekten denenen (401) en fazla 5; gerisi parolaya bakılmadan 429.
        assertThat(statuses).isSubsetOf(401, 429);
        assertThat(statuses.stream().filter(s -> s == 401).count()).isLessThanOrEqualTo(5);
        expect(login(phone, user.password(), ip), 429);
    }

    @Test
    void anInviteCanBeAcceptedOnlyOnceEvenInParallel() throws Exception {
        Account sender = register();
        Account invited = register();
        long invite = createInvite(sender, invited);
        String accept = "{\"wrappedKey\":\"wk\",\"seal\":\"seal\",\"fingerprint\":\"%s\",\"signature\":\"sig\"}"
                .formatted(sender.fingerprint());

        List<Integer> statuses = parallel(() -> call("POST", "/api/invites/" + invite + "/accept", accept, invited)
                .getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        List<?> inbox = JsonPath.read(body(call("GET", "/api/documents/inbox", null, invited)), "$.data");
        assertThat(inbox).hasSize(1);
    }

    @Test
    void parallelAccountDeletionAttemptsStillBlock() throws Exception {
        Account user = register();

        List<Integer> statuses = parallel(() -> call("POST", "/api/users/me/delete",
                "{\"password\":\"wrong-password\"}", user).getResponse().getStatus());

        assertThat(statuses).isSubsetOf(422, 429);
        assertThat(statuses.stream().filter(s -> s == 422).count()).isLessThanOrEqualTo(5);
        expect(call("POST", "/api/users/me/delete", "{\"password\":\"" + user.password() + "\"}", user), 429);
    }

    @Test
    void parallelPasswordChangeAttemptsStillBlock() throws Exception {
        Account user = register();
        String wrong = """
                {"currentPassword":"wrong-current","newPassword":"new-password-1",
                 "keys":{"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"b"}}"""
                .formatted(user.encryptionPublicKey(), user.signingPublicKey());

        List<Integer> statuses = parallel(() -> call("PUT", "/api/auth/password", wrong, user)
                .getResponse().getStatus());

        assertThat(statuses).isSubsetOf(422, 429);
        assertThat(statuses.stream().filter(s -> s == 422).count()).isLessThanOrEqualTo(5);
        String right = wrong.replace("wrong-current", user.password());
        expect(call("PUT", "/api/auth/password", right, user), 429);
    }

    private long createInvite(Account sender, Account invited) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[32]));
        MvcResult created = call("POST", "/api/invites", """
                {"recipientPhone":"%s","ttlHours":24,"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig",
                 "wrapped":"w","verifierHash":"%s","selfWrapped":"sw","lifetimeHours":24,"deleteOnOpen":false}"""
                .formatted(invited.phone().substring(3), hash), sender);
        expect(created, 200);
        return ((Number) JsonPath.read(body(created), "$.data.id")).longValue();
    }

    /** Aynı isteği PARALLEL kez, hepsi aynı anda başlayacak şekilde gönderir. */
    private static <T> List<T> parallel(Callable<T> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < PARALLEL; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
