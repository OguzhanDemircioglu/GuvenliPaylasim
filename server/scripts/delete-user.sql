-- Bir kullanıcıyı ve onunla ilgili her şeyi kalıcı olarak siler (AccountDeletionService.erase ile aynı sıra).
-- Kullanım: aşağıdaki numarayı değiştirip Railway → Postgres → Data → Query'de (ya da psql'de) çalıştırın.
-- Numara +90 ile, boşluksuz yazılır. Kullanıcı yoksa hiçbir şey silinmez. Geri alınamaz.
DO $$
DECLARE
    p    text := '+905527735994';
    uid  bigint;
    docs bigint[];
BEGIN
    SELECT id INTO uid FROM gps_v2.users WHERE phone = p;
    IF uid IS NULL THEN
        RAISE NOTICE 'Bu numarayla kullanıcı yok: %', p;
        RETURN;
    END IF;

    -- Gelen kutuları: önce kişinin alıcı satırları, sonra alıcısı kalmayan belgeler.
    SELECT array_agg(document_id) INTO docs FROM gps_v2.document_recipients WHERE recipient_id = uid;
    DELETE FROM gps_v2.document_recipients WHERE recipient_id = uid;
    DELETE FROM gps_v2.documents d
     WHERE d.id = ANY (coalesce(docs, '{}'))
       AND NOT EXISTS (SELECT 1 FROM gps_v2.document_recipients r WHERE r.document_id = d.id);

    -- Gönderdiği kutular, bütün alıcılarından.
    DELETE FROM gps_v2.document_recipients
     WHERE document_id IN (SELECT id FROM gps_v2.documents WHERE sender_id = uid);
    DELETE FROM gps_v2.documents WHERE sender_id = uid;

    DELETE FROM gps_v2.contacts WHERE owner_id = uid OR contact_id = uid;
    DELETE FROM gps_v2.invites WHERE sender_id = uid OR recipient_phone = p;
    DELETE FROM gps_v2.totp_recovery_codes WHERE user_id = uid;
    DELETE FROM gps_v2.user_sessions WHERE user_id = uid;
    DELETE FROM gps_v2.user_keys WHERE user_id = uid;
    DELETE FROM gps_v2.phone_verifications WHERE phone = p;
    DELETE FROM gps_v2.users WHERE id = uid;
    RAISE NOTICE 'Silindi: % (kullanıcı no %)', p, uid;
END $$;
