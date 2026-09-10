package io.github.jamielu.agent.reliability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用于确定性离线场景的进程内幂等存储。
 *
 * @param <R> 操作结果类型
 */
public final class InMemoryIdempotencyStore<R> implements IdempotencyStore<R> {
    private final Map<String, Entry<R>> entries = new HashMap<>();

    /** 创建空的进程内存储。 */
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


