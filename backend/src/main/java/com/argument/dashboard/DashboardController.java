package com.argument.dashboard;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {
    private final DashboardData data;
    private final VersionIdentity identity;
    public DashboardController(DashboardData data, VersionIdentity identity) { this.data=data; this.identity=identity; }
    @GetMapping("/api/v1/dashboard/summary")
    public ApiResponse<DashboardData.Summary> summary(
            @RequestHeader(value="Authorization", required=false) String auth,
            @RequestParam Map<String,String> query) {
        var actor=identity.authenticate(auth);
        if (!(actor.role("ADMIN") || actor.role("TASK_CREATOR")) || !actor.permission("TASK_OVERVIEW"))
            throw new BusinessException(ErrorCode.FORBIDDEN,"仅管理员或获授权任务创建者可查看管理统计");
        if (!Set.of("scope","page").containsAll(query.keySet())) throw invalid();
        if (query.containsKey("page")) {
            try { if (!query.get("page").matches("[1-9][0-9]*") || Integer.parseInt(query.get("page"))<1) throw invalid(); }
            catch (NumberFormatException e) { throw invalid(); }
        }
        // The shared contract has not defined a scope vocabulary. Never interpret arbitrary scope as wider access.
        if (query.containsKey("scope")) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"scope 取值尚待团队确认，请省略；统计始终受当前身份范围限制");
        return ApiResponse.ok(data.summary(actor));
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR,"统计查询参数不符合约定"); }
}
