package com.server.modules.key;

import com.server.modules.key.dto.RecipientResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface UserKeyRepository extends JpaRepository<UserKey, Long> {

    /** Seçilen birimlerdeki, anahtarı olan aktif kullanıcılar (çağıran hariç). */
    @Query("""
            SELECT new com.server.modules.key.dto.RecipientResponse(
                       u.id, u.fullName, u.unitId, k.encryptionPublicKey, k.signingPublicKey)
              FROM User u JOIN UserKey k ON k.userId = u.id
             WHERE u.active = true AND u.unitId IN :unitIds AND u.id <> :excludedUserId
             ORDER BY u.fullName""")
    List<RecipientResponse> findRecipients(Collection<Long> unitIds, Long excludedUserId);
}
