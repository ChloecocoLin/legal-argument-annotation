package com.argument.proposition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link PropositionMappingSource} 的**替身实现**：按「展示编号优先、原文兜底」对齐两个快照。
 *
 * <p>替身依据：《核心标注与裁定数据模型》§Mock 场景里两名标注员切割的是**同一文书同一文本版本**，
 * 经统一切割关口后 P1…Pn 一一对应；同编号即同一命题，`v1-p2` 与 `v2-p2` 原文相同。
 *
 * <p>它**不是**真实的命题裁定：真正决定「V2 的 P3 是否并入统一命题集合的 P3」的是 B 的裁定项。
 * 因此本类只做对齐、不做合并、不改写任何源数据；B 的裁定项就绪后换实现即可（见分工.md §七）。
 */
@Service
@RequiredArgsConstructor
public class MockPropositionMapping implements PropositionMappingSource {

    private final PropositionSource propositions;

    @Override
    public Map<String, String> mappingTo(String sourceSnapshotId, String unifiedSnapshotId) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (sourceSnapshotId == null || unifiedSnapshotId == null) {
            return mapping;
        }
        List<PropositionRef> unified = propositions.findBySnapshot(unifiedSnapshotId);
        Map<String, String> byDisplayNo = new LinkedHashMap<>();
        Map<String, String> byText = new LinkedHashMap<>();
        for (PropositionRef target : unified) {
            if (target.displayNo() != null) {
                byDisplayNo.putIfAbsent(target.displayNo(), target.id());
            }
            byText.putIfAbsent(target.text(), target.id());
        }
        for (PropositionRef source : propositions.findBySnapshot(sourceSnapshotId)) {
            String matched = source.displayNo() == null
                    ? null : byDisplayNo.get(source.displayNo());
            if (matched == null) {
                matched = byText.get(source.text());
            }
            if (matched != null) {
                mapping.put(source.id(), matched);
            }
        }
        return mapping;
    }
}
