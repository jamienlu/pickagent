package com.pickagent.w2.reliability;

/**
 * Provider-neutral failure categories used by the retry boundary.
 *
 * <p>HTTP status codes and provider error payloads are translated into these
 * categories before the policy is called. In particular, not every HTTP 429
 * is a transient rate limit.</p>
 */
public enum FailureKind {
    /** A temporary request/token/ramp rate limit. */
    TRANSIENT_RATE_LIMIT(true),
    /** A provider or model that is temporarily overloaded. */
    SERVICE_OVERLOADED(true),
    /** A transport or provider timeout. */
    TIMEOUT(true),
    /** A credit, billing, spend-limit or usage-quota failure. */
    BILLING_OR_QUOTA(false),
    /** Invalid, expired, revoked or unauthorized credentials. */
    AUTHENTICATION(false),
    /** A malformed or semantically invalid request. */
    INVALID_REQUEST(false);

    private final boolean retryable;

    FailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Reports whether retrying can be useful for this category.
     *
     * @return whether this category can be retried when budgets still allow it
     */
    public boolean retryable() {
        return retryable;
    }
}
