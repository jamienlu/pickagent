package io.github.jamielu.agent.reliability;

/** 供重试边界使用的供应商中立失败分类。 */
public enum FailureKind {
    /** 临时请求、令牌或爬坡限流。 */
    TRANSIENT_RATE_LIMIT(true),
    /** 供应商或模型暂时过载。 */
    SERVICE_OVERLOADED(true),
    /** 传输或供应商超时。 */
    TIMEOUT(true),
    /** 余额、账单、消费上限或额度失败。 */
    BILLING_OR_QUOTA(false),
    /** 无效、过期、撤销或未授权凭据。 */
    AUTHENTICATION(false),
    /** 格式或语义无效的请求。 */
    INVALID_REQUEST(false);

    private final boolean retryable;

    FailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * 判断该失败分类是否值得在预算内重试。
     *
     * @return 该分类可在预算内重试时为 {@code true}
     */
    public boolean retryable() {
        return retryable;
    }
}


