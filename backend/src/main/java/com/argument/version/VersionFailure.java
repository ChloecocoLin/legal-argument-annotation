package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import java.util.Map;

/** Rich conflict details are contract DTOs, not public database models. */
public class VersionFailure extends BusinessException {
    private final Map<String,Object> detail;
    public VersionFailure(ErrorCode code,String message,Map<String,Object> detail) { super(code,message); this.detail=detail; }
    public Map<String,Object> detail() { return detail; }
}
