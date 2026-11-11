package com.server.acceptance;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Başka kullanıcının kaydına erişim denemeleri (IDOR, Security.md S4, ASVS V4.2). Saldırgan geçerli bir oturumla
 * gelir ve kurbanın kayıt numaralarını bilir. Beklenen: kayıt "yok" gibi davranır (404) ve hiçbir şey değişmez.
 */
class AccessControlAcceptanceTest extends AcceptanceTest {

    // --- Belgeler ---

    @Test
    void strangerCannotOpenMarkOrDeleteSomeoneElsesDocument() throws Exception {
        Account sender = register();
        Account recipient = register();
        Account stranger = register();
        connect(sender, recipient);
        long id = sendDocument(sender, recipient);

        expect(call("GET", "/api/documents/" + id, null, stranger), 404);
        expect(call("POST", "/api/documents/" + id + "/opened", null, stranger), 404);
        expect(call("DELETE", "/api/documents/" + id, null, stranger), 404);

        expect(call("GET", "/api/documents/" + id, null, recipient), 200);
    }

    @Test
    void senderCannotOpenTheBoxThroughTheRecipientEndpoint() throws Exception {
        Account sender = register();
        Account recipient = register();
        connect(sender, recipient);
        long id = sendDocument(sender, recipient);

        expect(call("GET", "/api/documents/" + id, null, sender), 404);
        expect(call("DELETE", "/api/documents/" + id, null, sender), 404);
        expect(call("GET", "/api/documents/" + id, null, recipient), 200);
    }

    @Test
    void oneRecipientDeletingDoesNotRemoveTheBoxForTheOther() throws Exception {
        Account sender = register();
        Account first = register();
        Account second = register();
        connect(sender, first);
        connect(sender, second);
        long id = sendDocument(sender, first, second);

        expect(call("DELETE", "/api/documents/" + id, null, first), 200);

        expect(call("GET", "/api/documents/" + id, null, first), 404);
        expect(call("GET", "/api/documents/" + id, null, second), 200);
    }

    @Test
    void inboxAndSentListsShowOnlyTheCallersOwnBoxes() throws Exception {
        Account sender = register();
        Account recipient = register();
        Account stranger = register();
        connect(sender, recipient);
        long id = sendDocument(sender, recipient);

        assertThat(ids(call("GET", "/api/documents/inbox", null, stranger))).doesNotContain(id);
        assertThat(ids(call("GET", "/api/documents/sent", null, stranger))).doesNotContain(id);
        assertThat(ids(call("GET", "/api/documents/inbox", null, recipient))).contains(id);
        assertThat(ids(call("GET", "/api/documents/sent", null, sender))).contains(id);
    }

    @Test
    void cannotSendToSomeoneWhoIsNotAVerifiedContact() throws Exception {
        Account sender = register();
        Account target = register();

        MvcResult sent = call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[{"userId":%d,"wrappedKey":"wk"}]}""".formatted(target.id()), sender);

        expect(sent, 422);
        assertThat(ids(call("GET", "/api/documents/inbox", null, target))).isEmpty();
    }

    @Test
    void cannotAddAStrangerAsSecondRecipientOfABoxToAContact() throws Exception {
        Account sender = register();
        Account contact = register();
        Account stranger = register();
        connect(sender, contact);

        MvcResult sent = call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[{"userId":%d,"wrappedKey":"wk"},{"userId":%d,"wrappedKey":"wk"}]}"""
                .formatted(contact.id(), stranger.id()), sender);

        expect(sent, 422);
        assertThat(ids(call("GET", "/api/documents/inbox", null, contact))).isEmpty();
    }

    // --- Kişiler ---

    @Test
    void deletingAContactOnlyAffectsTheCallersOwnRelations() throws Exception {
        Account a = register();
        Account b = register();
        Account stranger = register();
        connect(a, b);

        expect(call("DELETE", "/api/contacts/" + b.id(), null, stranger), 404);

        assertThat(contactIds(a)).contains(b.id());
        assertThat(contactIds(b)).contains(a.id());
        assertThat(contactIds(stranger)).isEmpty();
    }

    // --- Davetler ---

    @Test
    void onlyTheInvitedNumberCanRedeemAndAcceptAnInvite() throws Exception {
        Account sender = register();
        Account invited = register();
        Account stranger = register();
        byte[] verifier = new byte[32];
        new SecureRandom().nextBytes(verifier);
        long invite = createInvite(sender, invited, verifier);
        String verifierBase64 = Base64.getEncoder().encodeToString(verifier);

        // Kodu (verifier) ele geçiren başka biri bile davete ulaşamaz: davet numaraya bağlı.
        expect(call("POST", "/api/invites/redeem", "{\"verifier\":\"" + verifierBase64 + "\"}", stranger), 422);
        expect(call("POST", "/api/invites/" + invite + "/accept", accept(sender), stranger), 404);

        expect(call("POST", "/api/invites/redeem", "{\"verifier\":\"" + verifierBase64 + "\"}", invited), 200);
    }

    @Test
    void onlyTheSenderCanCancelVerifyOrRejectAnInvite() throws Exception {
        Account sender = register();
        Account invited = register();
        Account stranger = register();
        long pending = createInvite(sender, invited, new byte[32]);

        expect(call("DELETE", "/api/invites/" + pending, null, stranger), 404);
        expect(call("DELETE", "/api/invites/" + pending, null, invited), 404);

        long accepted = createInvite(sender, invited, new byte[32]);
        expect(call("POST", "/api/invites/" + accepted + "/accept", accept(sender), invited), 200);
        String verify = "{\"fingerprint\":\"%s\",\"signature\":\"sig\"}".formatted(invited.fingerprint());
        expect(call("POST", "/api/invites/" + accepted + "/verify", verify, stranger), 404);
        expect(call("POST", "/api/invites/" + accepted + "/reject", null, stranger), 404);
        expect(call("POST", "/api/invites/" + accepted + "/verify", verify, invited), 404);

        expect(call("POST", "/api/invites/" + accepted + "/verify", verify, sender), 200);
        expect(call("DELETE", "/api/invites/" + pending, null, sender), 200);
    }

    @Test
    void invitationListsShowOnlyTheCallersOwnInvites() throws Exception {
        Account sender = register();
        Account invited = register();
        Account stranger = register();
        long invite = createInvite(sender, invited, new byte[32]);

        assertThat(ids(call("GET", "/api/invites/sent", null, stranger))).doesNotContain(invite);
        assertThat(ids(call("GET", "/api/invites/sent", null, sender))).contains(invite);
    }

    // --- Anahtarlar ---

    @Test
    void keysCannotBeReplacedThroughTheUploadEndpoint() throws Exception {
        Account user = register();
        String other = """
                {"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"evil"}"""
                .formatted(register().encryptionPublicKey(), user.signingPublicKey());

        expect(call("POST", "/api/keys/me", other, user), 422);
        String own = body(call("GET", "/api/keys/me", null, user));
        assertThat((String) JsonPath.read(own, "$.data.encryptionPublicKey")).isEqualTo(user.encryptionPublicKey());
    }

    @Test
    void passwordChangeCannotSwapPublicKeys() throws Exception {
        Account user = register();
        String change = """
                {"currentPassword":"%s","newPassword":"%s",
                 "keys":{"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"evil"}}"""
                .formatted(user.password(), user.password() + "x", register().encryptionPublicKey(),
                        user.signingPublicKey());

        expect(call("PUT", "/api/auth/password", change, user), 422);
        expect(login(user.phone().substring(3), user.password(), randomIp()), 200);
    }

    // --- Yardımcılar ---

    private long createInvite(Account sender, Account invited, byte[] verifier) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(verifier));
        MvcResult created = call("POST", "/api/invites", """
                {"recipientPhone":"%s","ttlHours":24,"kind":"TEXT","payload":"Z2l6bGk=","signature":"sig",
                 "wrapped":"w","verifierHash":"%s","selfWrapped":"sw","lifetimeHours":24,"deleteOnOpen":false}"""
                .formatted(invited.phone().substring(3), hash), sender);
        expect(created, 200);
        return ((Number) JsonPath.read(body(created), "$.data.id")).longValue();
    }

    private static String accept(Account sender) {
        return "{\"wrappedKey\":\"wk\",\"seal\":\"seal\",\"fingerprint\":\"%s\",\"signature\":\"sig\"}"
                .formatted(sender.fingerprint());
    }

    private List<Long> contactIds(Account owner) throws Exception {
        MvcResult list = call("GET", "/api/contacts", null, owner);
        expect(list, 200);
        List<Number> ids = JsonPath.read(body(list), "$.data[*].userId");
        return ids.stream().map(Number::longValue).toList();
    }

    private static List<Long> ids(MvcResult list) throws Exception {
        expect(list, 200);
        List<Number> ids = JsonPath.read(body(list), "$.data[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
