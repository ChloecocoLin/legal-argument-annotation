package com.argument.common.exception;

import com.argument.common.response.ApiResponse;
import com.argument.common.response.FieldRule;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。统一输出失败信封。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        ApiResponse<Object> body = ApiResponse.fail(code, ex.getMessage());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        List<FieldRule> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new FieldRule(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败", details);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        List<FieldRule> details = new ArrayList<>();
        ex.getConstraintViolations()
                .forEach(
                        v ->
                                details.add(
                                        new FieldRule(
                                                v.getPropertyPath().toString(), v.getMessage())));
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败", details);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("未处理异常", ex);
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误");
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(body);
    }
}
