package io.github.jamielu.agent.reliability;

import java.util.Objects;

/**
 * 针对稳定业务键和请求返回首次成功结果。
 *
 * @param <R> 操作结果类型
 */
public final class IdempotentExecutor<R> {
    /**
     * 可能在产生可缓存结果前失败的操作。
     *
     * @param <R> 操作结果类型
     */
    @FunctionalInterface
    public interface Operation<R> {
        /**
         * 执行底层操作。
         *
         * @return 操作结果
         * @throws Exception 底层操作失败
         */
        R execute() throws Exception;
    }

    /** 同一业务键被不同逻辑请求复用时抛出的冲突。 */
    public static final class IdempotencyConflictException extends IllegalStateException {
        /**
         * 使用诊断消息创建冲突。
         *
         * @param message 不包含秘密的诊断消息
         */
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    private final IdempotencyStore<R> store;

    /**
     * 创建由给定存储支持的执行器。
     *
     * @param store 成功结果存储端口
     */
    public IdempotentExecutor(IdempotencyStore<R> store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * 缓存未命中时执行一次，否则重放首次成功结果。
     *
     * @param operationKey 稳定业务操作键
     * @param requestFingerprint 请求内容的稳定指纹
     * @param operation 缓存未命中时执行的操作
     * @return 首次成功结果或其重放值
     * @throws Exception 底层操作失败
     */
    public R execute(String operationKey, String requestFingerprint, Operation<R> operation)
            throws Exception {
        requireNonBlank(operationKey, "operationKey");
        requireNonBlank(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(operation, "operation");

        var existing = store.find(operationKey);
        if (existing.isPresent()) {
            var entry = existing.orElseThrow();
            if (!entry.requestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException(
                        "operationKey already belongs to a different request: " + operationKey);
            }
            return entry.result();
        }

        R result = operation.execute();
        store.save(operationKey, new IdempotencyStore.Entry<>(requestFingerprint, result));
        return result;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}


