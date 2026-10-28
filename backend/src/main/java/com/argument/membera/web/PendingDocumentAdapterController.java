package com.argument.membera.web;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** D02/D04/D06/D07/D08 为 PENDING_MODEL：只提供适配层，不自创正式表结构。 */
@RestController
@RequestMapping("/api/v1")
public class PendingDocumentAdapterController {

    @PostMapping("/documents")
    public ApiResponse<Map<String, Object>> d02() {
        throw pending("D02");
    }

    @PostMapping("/documents/{documentId}/text-versions")
    public ApiResponse<Map<String, Object>> d04() {
        throw pending("D04");
    }

    @PostMapping("/document-text-versions/{textVersionId}/scopes")
    public ApiResponse<Map<String, Object>> d06() {
        throw pending("D06");
    }

    @GetMapping("/documents/{documentId}/annotation-overview")
    public ApiResponse<Map<String, Object>> d07(@PathVariable String documentId) {
        return ApiResponse.ok(
                Map.of(
                        "finalVersionId", "7ae1dfb2-7f6a-4351-98cd-953102ff2719",
                        "graphIds", java.util.List.of("efb4e906-a58f-4764-981e-d6be7daaa7fa"),
                        "documentId", documentId,
                        "adapter", "PENDING_MODEL mock"));
    }

    @PostMapping("/document-text-versions/{textVersionId}/publish")
    public ApiResponse<Map<String, Object>> d08() {
        throw pending("D08");
    }

    private BusinessException pending(String code) {
        return new BusinessException(ErrorCode.PENDING_MODEL, code + " 模型未冻结，请使用预置文书/范围，不要当作正式入库接口");
    }
}
