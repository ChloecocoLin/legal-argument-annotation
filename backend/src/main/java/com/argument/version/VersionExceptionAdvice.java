package com.argument.version;

import com.argument.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages={"com.argument.version","com.argument.adjudication","com.argument.finalreview","com.argument.committrial","com.argument.exporttrial","com.argument.segmentationtrial","com.argument.correctiontrial","com.argument.resulttrial","com.argument.membera","com.argument.auth","com.argument.draft"})
public class VersionExceptionAdvice {
    @ExceptionHandler(VersionFailure.class)
    public ResponseEntity<Map<String,Object>> conflict(VersionFailure ex) {
        return failure(ex.getErrorCode(),ex.getMessage(),List.of(ex.detail()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception ex) {
        return failure(ErrorCode.VALIDATION_ERROR,"请求格式不正确",List.of());
    }
    private ResponseEntity<Map<String,Object>> failure(ErrorCode code,String message,List<?> details) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("success",false); body.put("data",null); body.put("meta",null);
        body.put("error",Map.of("code",code.getCode(),"message",message,"details",details));
        body.put("requestId",VersionJson.uuid()); body.put("timestamp",VersionJson.now());
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
