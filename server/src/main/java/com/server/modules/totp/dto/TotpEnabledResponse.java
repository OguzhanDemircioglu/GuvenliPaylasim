package com.server.modules.totp.dto;

import java.util.List;

/** Kurtarma kodları sadece burada, bir kez gösterilir. */
public record TotpEnabledResponse(List<String> recoveryCodes) {
}
