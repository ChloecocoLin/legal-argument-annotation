package com.argument.common.exception;

import com.argument.common.response.ApiResponse;
import com.argument.common.response.FieldRule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    /**
     * 方法级鉴权失败（{@code @PreAuthorize} 不通过）。
     *
     * <p>这一条**必须显式写**，否则会被下面的 {@link Exception} 兜底吃掉，变成 500 INTERNAL_ERROR：
     * 控制器上的 {@code @PreAuthorize} 是在 DispatcherServlet 内部抛 {@link AccessDeniedException} 的，
     * 而带 {@code @RestControllerAdvice} 的处理器在 Servlet 链路里比
     * {@code ExceptionTranslationFilter} 更靠内，异常根本走不到 Spring Security 的
     * {@code AccessDeniedHandler}。表现出来就是「没权限的请求返回 500」——前端只会说「服务器错误」，
     * 排查时会一路怀疑业务代码。（URL 级拒绝仍由 jsonAccessDeniedHandler 处理，两者输出同一个信封。）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        // 记 WARN 且不带堆栈：403 是正常的业务结果（角色不对），不是程序错误。
        // 堆栈留下来只会让日志像崩了，真正需要的是「谁、以什么身份、想访问什么」。
        log.warn(
                "鉴权拒绝：user={} {} {}",
                currentUsername(),
                requestMethod(),
                requestUri());
        ApiResponse<Object> body = ApiResponse.fail(ErrorCode.FORBIDDEN, "没有操作权限");
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus()).body(body);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? "(未登录)" : auth.getName();
    }

    private String requestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getMethod();
    }

    private String requestUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getRequestURI();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Object>> missingBinding(Exception ex) { return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR,"缺少必要的请求头或参数")); }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> resourceNotFound(Exception ex) {
        return ResponseEntity.status(404).body(ApiResponse.fail(ErrorCode.NOT_FOUND, "请求的资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("未处理异常", ex);
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误");
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(body);
    }
}
