package io.github.jamielu.agent.openai;

import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import com.openai.errors.UnexpectedStatusCodeException;
import io.github.jamielu.agent.reliability.FailureKind;
import io.github.jamielu.agent.runtime.ModelExecutionException;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** 将 OpenAI SDK 异常转换为供应商中立、可审计且不泄漏凭据的失败。 */
public final class OpenAiExceptionMapper {
    /** 创建无状态 SDK 异常映射器。 */
    public OpenAiExceptionMapper() {
    }

    /**
     * 映射 SDK 异常。
     *
     * @param failure SDK 异常
     * @return 供应商中立模型失败
     */
    public ModelExecutionException map(OpenAIException failure) {
        Objects.requireNonNull(failure, "failure");
        FailureKind kind = classify(failure);
        return new ModelExecutionException(kind,
                "OpenAI Responses request failed: " + kind.name().toLowerCase(Locale.ROOT),
                failure);
    }

    private FailureKind classify(OpenAIException failure) {
        if (failure instanceof RateLimitException rateLimit) {
            return isQuotaFailure(rateLimit)
                    ? FailureKind.BILLING_OR_QUOTA
                    : FailureKind.TRANSIENT_RATE_LIMIT;
        }
        if (failure instanceof OpenAIIoException) {
            return FailureKind.TIMEOUT;
        }
        if (failure instanceof InternalServerException) {
            return FailureKind.SERVICE_OVERLOADED;
        }
        if (failure instanceof UnauthorizedException
                || failure instanceof PermissionDeniedException) {
            return FailureKind.AUTHENTICATION;
        }
        if (failure instanceof BadRequestException
                || failure instanceof UnprocessableEntityException
                || failure instanceof NotFoundException) {
            return FailureKind.INVALID_REQUEST;
        }
        if (failure instanceof UnexpectedStatusCodeException unexpected
                && unexpected.statusCode() >= 500) {
            return FailureKind.SERVICE_OVERLOADED;
        }
        return FailureKind.INVALID_REQUEST;
    }

    private static boolean isQuotaFailure(OpenAIServiceException failure) {
        String searchable = Stream.of(failure.code(), failure.type())
                .flatMap(java.util.Optional::stream)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        return searchable.contains("quota")
                || searchable.contains("billing")
                || searchable.contains("credit");
    }
}
