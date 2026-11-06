package com.server.modules.invite.dto;

/** Davetin karşı tarafı ve güncel açık anahtarları. İstemci parmak izini koddan gelen bilgiyle karşılaştırır. */
public record PartyResponse(Long userId, String fullName, String phone, String encryptionPublicKey,
                            String signingPublicKey) {
}
