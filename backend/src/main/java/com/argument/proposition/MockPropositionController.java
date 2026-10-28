package com.argument.proposition;

import com.argument.common.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 【临时桥接】标注工作台的命题数据来源。归成员 C，**不是契约接口**。
 *
 * <p>分工.md §七 允许 C 在标注页使用模拟命题 JSON。此处把它放到后端，使关系校验器
 * 能真校验「非命题不能参与关系」「同一快照」「I 仅命题」等规则。A 的文书范围与 B 的
 * 要素/命题接口（E01…）就绪后，前端只改 {@code api/relation.ts} 里的单个函数即可切走。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MockPropositionController {

    private final PropositionSource propositions;

    // 【临时】列出草稿下的模拟命题（含非命题，供原文列与图示渲染）
    @GetMapping("/annotation-drafts/{draftId}/mock-propositions")
    public ApiResponse<Map<String, Object>> listMockPropositions(@PathVariable String draftId) {
        List<PropositionRef> items = propositions.findBySnapshot(draftId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        return ApiResponse.ok(data);
    }
}
