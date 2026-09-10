package io.github.jamielu.agent.reliability;

import java.util.Optional;

/**
 * 成功幂等操作结果的存储端口。
 *
 * @param <R> 操作结果类型
 */
public interface IdempotencyStore<R> {
    /**
     * 查找先前保存的条目。
     *
     * @param operationKey 稳定业务操作键
     * @return 已保存条目；不存在时为空
     */
    Optional<Entry<R>> find(String operationKey);

    /**
     * 保存一次成功操作结果。
     *
     * @param operationKey 稳定业务操作键
     * @param entry 请求身份与成功结果
     */
    void save(String operationKey, Entry<R> entry);

    /**
     * 与业务键关联的请求身份及首次成功结果。
     *
     * @param <R> 操作结果类型
     * @param requestFingerprint 请求内容的稳定指纹
     * @param result 首次成功结果
     */
    record Entry<R>(String requestFingerprint, R result) {
        /** 校验请求指纹。 */
        public Entry {
            if (requestFingerprint == null || requestFingerprint.isBlank()) {
                throw new IllegalArgumentException("requestFingerprint cannot be null or blank");
            }
        }
    }
}


