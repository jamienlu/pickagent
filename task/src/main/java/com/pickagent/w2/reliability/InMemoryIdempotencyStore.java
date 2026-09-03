package com.pickagent.w2.reliability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Process-local store for deterministic offline use.
 *
 * <p>Concurrency, persistence and cross-process recovery are intentionally out
 * of scope for this exercise.</p>
 *
 * @param <R> operation result type
 */
public final class InMemoryIdempotencyStore<R> implements IdempotencyStore<R> {
    private final Map<String, Entry<R>> entries = new HashMap<>();

    /** Creates an empty process-local store. */
    public InMemoryIdempotencyStore() {
    }

    @Override
    public Optional<Entry<R>> find(String operationKey) {
        return Optional.ofNullable(entries.get(operationKey));
    }

    @Override
    public void save(String operationKey, Entry<R> entry) {
        entries.put(operationKey, entry);
    }
}
