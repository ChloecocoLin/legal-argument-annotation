package com.argument.membera.web;

import com.argument.common.response.ApiResponse;
import com.argument.membera.service.DocumentService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/documents")
    public ApiResponse<Map<String, Object>> d01(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String courtLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DocumentService.Result result = documentService.list(q, sourceType, caseType, courtLevel, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<Map<String, Object>> d03(@PathVariable String documentId) {
        return ApiResponse.ok(documentService.detail(documentId));
    }

    @GetMapping("/document-text-versions/{textVersionId}")
    public ApiResponse<Map<String, Object>> d05(@PathVariable String textVersionId) {
        return ApiResponse.ok(documentService.textVersion(textVersionId));
    }
}
