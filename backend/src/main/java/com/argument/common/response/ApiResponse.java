package com.argument.common.response;

import com.argument.common.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;

/**
 * 统一响应信封，与 openapi-ready.yaml 对齐：
 * 成功 { success=true, data, meta, error=null, requestId, timestamp }
 * 失败 { success=false, data=null, meta=null, error, requestId, timestamp }
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final Object meta;
    private final ErrorInfo error;
    private final String requestId;
    private final OffsetDateTime timestamp;

    private ApiResponse(boolean success, T data, Object meta, ErrorInfo error) {
        this.success = success;
        this.data = data;
        this.meta = meta;
        this.error = error;
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        ErrorInfo info = new ErrorInfo(errorCode.getCode(), message, java.util.List.of());
        return new ApiResponse<>(false, null, null, info);
    }

    public static <T> ApiResponse<T> fail(
            ErrorCode errorCode, String message, java.util.List<FieldRule> details) {
        ErrorInfo info = new ErrorInfo(errorCode.getCode(), message, details);
        return new ApiResponse<>(false, null, null, info);
    }
}
