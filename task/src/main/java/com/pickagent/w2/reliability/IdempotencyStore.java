package com.pickagent.w2.reliability;

import java.util.Optional;

/**
 * Storage port for successful idempotent operation results.
 *
 * @param <R> operation result type
 */
public interface IdempotencyStore<R> {
    /**
     * Finds a previously saved entry.
     *
     * @param operationKey stable application operation key
     * @return saved entry, or empty on a cache miss
     */
    Optional<Entry<R>> find(String operationKey);

    /**
     * Saves a successful operation result.
     *
     * @param operationKey stable application operation key
     * @param entry request identity and result
     */
    void save(String operationKey, Entry<R> entry);

    /**
     * The request identity and first successful result associated with a key.
     *
     * @param requestFingerprint stable request fingerprint
     * @param result first successful result
     * @param <R> operation result type
     */
    record Entry<R>(String requestFingerprint, R result) {
        /** Validates the request fingerprint. */
        public Entry {
            if (requestFingerprint == null || requestFingerprint.isBlank()) {
                throw new IllegalArgumentException("requestFingerprint cannot be null or blank");
            }
        }
    }
}
