package com.argument.membera.service;

import com.argument.common.auth.AuthContext;
import com.argument.common.auth.DevUser;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.PageMeta;
import com.argument.membera.store.Records;
import com.argument.membera.store.WorkflowStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final WorkflowStore store;
    private final AccessService access;

    public DocumentService(WorkflowStore store, AccessService access) {
        this.store = store;
        this.access = access;
    }

    public Result list(String q, String sourceType, String caseType, String courtLevel, int page, int size) {
        DevUser user = AuthContext.requireAnyRole("ADMIN", "TASK_CREATOR", "ANNOTATOR", "ARBITER");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> doc : store.documents.values()) {
            if (!access.canViewDocument(user, Records.str(doc, "id"))) {
                continue;
            }
            if (sourceType != null && !sourceType.isBlank() && !sourceType.equals(Records.str(doc, "sourceType"))) {
                continue;
            }
            if (caseType != null && !caseType.isBlank() && !caseType.equals(Records.str(doc, "caseType"))) {
                continue;
            }
            if (courtLevel != null && !courtLevel.isBlank() && !courtLevel.equals(Records.str(doc, "courtLevel"))) {
                continue;
            }
            if (q != null && !q.isBlank()) {
                String hay =
                        (Records.str(doc, "title")
                                        + Records.str(doc, "caseNumber")
                                        + Records.str(doc, "courtName")
                                        + Records.str(doc, "causeOfAction"))
                                .toLowerCase(Locale.ROOT);
                if (!hay.contains(q.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", doc.get("id"));
            item.put("courtName", doc.get("courtName"));
            item.put("causeOfAction", doc.get("causeOfAction"));
            items.add(item);
        }
        return paginate(items, page, size);
    }

    public Map<String, Object> detail(String documentId) {
        DevUser user = AuthContext.require();
        Map<String, Object> doc = store.documents.get(documentId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文书不存在");
        }
        if (!access.canViewDocument(user, documentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该文书");
        }
        List<String> versionIds = new ArrayList<>();
        for (Map<String, Object> tv : store.textVersions.values()) {
            if (documentId.equals(Records.str(tv, "documentId"))) {
                versionIds.add(Records.str(tv, "id"));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", doc.get("id"));
        data.put("originalFileId", doc.get("originalFileId"));
        data.put("textVersionIds", versionIds);
        return data;
    }

    public Map<String, Object> textVersion(String textVersionId) {
        DevUser user = AuthContext.require();
        Map<String, Object> tv = store.textVersions.get(textVersionId);
        if (tv == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文本版本不存在");
        }
        if (!access.canViewDocument(user, Records.str(tv, "documentId"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权读取该文本版本");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", tv.get("id"));
        data.put("normalizedText", tv.get("normalizedText"));
        data.put("contentSha256", tv.get("contentSha256"));
        return data;
    }

    public static Result paginate(List<Map<String, Object>> items, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items.subList(from, to));
        return new Result(data, new PageMeta(safePage, safeSize, items.size()));
    }

    public record Result(Map<String, Object> data, PageMeta meta) {}
}
