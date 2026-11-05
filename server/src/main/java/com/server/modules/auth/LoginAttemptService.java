package com.server.modules.auth;

public interface LoginAttemptService {

    /** Engelliyse 429 fırlatır; engel süresince doğru şifre de kabul edilmez. */
    void ensureNotBlocked(String attemptKey);

    void recordFailure(String attemptKey);

    void clear(String attemptKey);
}
