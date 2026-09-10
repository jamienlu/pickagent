package io.github.jamielu.agent.openai;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import io.github.jamielu.agent.reliability.FailureKind;
import io.github.jamielu.agent.runtime.ModelExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiExceptionMapperTest {
    private final OpenAiExceptionMapper mapper = new OpenAiExceptionMapper();

    // 场景：429 错误携带额度耗尽代码；行为：映射 SDK 异常；预期：归类为不可重试的账单或额度失败。
    @Test
    void mapsQuotaRateLimit() {
        assertKind(FailureKind.BILLING_OR_QUOTA,
                RateLimitException.builder().headers(headers())
                        .error(error("insufficient_quota", "rate_limit_error")).build());
    }

    // 场景：429 错误携带账单或余额类型；行为：映射 SDK 异常；预期：两种关键词均归入额度类。
    @Test
    void mapsBillingAndCreditRateLimits() {
        assertKind(FailureKind.BILLING_OR_QUOTA,
                RateLimitException.builder().headers(headers())
                        .error(error("billing_hard_limit", "rate_limit_error")).build());
        assertKind(FailureKind.BILLING_OR_QUOTA,
                RateLimitException.builder().headers(headers())
                        .error(error("rate_limit", "credit_balance_exhausted")).build());
    }

    // 场景：普通临时 429 没有额度关键词；行为：映射 SDK 异常；预期：归类为可重试限流。
    @Test
    void mapsTransientRateLimit() {
        assertKind(FailureKind.TRANSIENT_RATE_LIMIT,
                RateLimitException.builder().headers(headers())
                        .error(error("rate_limit_exceeded", "requests")).build());
    }

    // 场景：传输、服务端和鉴权异常；行为：逐一映射；预期：得到稳定且互不混淆的核心失败分类。
    @Test
    void mapsTransportServerAndAuthenticationFailures() {
        assertKind(FailureKind.TIMEOUT, new OpenAIIoException("timeout"));
        assertKind(FailureKind.SERVICE_OVERLOADED,
                InternalServerException.builder().statusCode(503).headers(headers())
                        .error(error("overloaded", "server_error")).build());
        assertKind(FailureKind.AUTHENTICATION,
                UnauthorizedException.builder().headers(headers())
                        .error(error("invalid_api_key", "auth")).build());
        assertKind(FailureKind.AUTHENTICATION,
                PermissionDeniedException.builder().headers(headers())
                        .error(error("forbidden", "auth")).build());
    }

    // 场景：客户端请求错误；行为：映射三类 SDK 异常；预期：均归入不可重试的无效请求。
    @Test
    void mapsKnownClientFailures() {
        assertKind(FailureKind.INVALID_REQUEST,
                BadRequestException.builder().headers(headers())
                        .error(error("bad", "request")).build());
        assertKind(FailureKind.INVALID_REQUEST,
                UnprocessableEntityException.builder().headers(headers())
                        .error(error("invalid", "request")).build());
        assertKind(FailureKind.INVALID_REQUEST,
                NotFoundException.builder().headers(headers())
                        .error(error("missing", "request")).build());
    }

    // 场景：SDK 返回未预定义状态异常；行为：按状态码映射；预期：5xx 为过载，非 5xx 保守视为无效请求。
    @Test
    void mapsUnexpectedStatusByStatusFamily() {
        assertKind(FailureKind.SERVICE_OVERLOADED,
                unexpected(599));
        assertKind(FailureKind.INVALID_REQUEST,
                unexpected(418));
    }

    // 场景：SDK 增加未知异常子类；行为：执行兜底映射；预期：不误重试并且诊断消息不复述底层敏感文本。
    @Test
    void mapsUnknownSdkFailureConservativelyWithoutLeakingMessage() {
        OpenAIException source = new OpenAIException("secret-token-value");

        ModelExecutionException mapped = mapper.map(source);

        assertEquals(FailureKind.INVALID_REQUEST, mapped.kind());
        assertSame(source, mapped.getCause());
        assertFalse(mapped.getMessage().contains("secret-token-value"));
    }

    // 场景：映射器收到空异常；行为：执行映射；预期：立即拒绝空输入。
    @Test
    void rejectsNullFailure() {
        assertThrows(NullPointerException.class, () -> mapper.map(null));
    }

    private void assertKind(FailureKind expected, OpenAIException source) {
        ModelExecutionException mapped = mapper.map(source);
        assertEquals(expected, mapped.kind());
        assertSame(source, mapped.getCause());
    }

    private static UnexpectedStatusCodeException unexpected(int status) {
        return UnexpectedStatusCodeException.builder().statusCode(status).headers(headers())
                .error(error("unexpected", "status")).build();
    }

    private static Headers headers() {
        return Headers.builder().build();
    }

    private static ErrorObject error(String code, String type) {
        return ErrorObject.builder()
                .code(code)
                .message("safe test error")
                .param(java.util.Optional.empty())
                .type(type)
                .build();
    }
}
