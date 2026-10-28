package com.argument.proposition;

import com.argument.proposition.mapper.MockPropositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link PropositionSource} 的临时实现：读 mock_proposition 临时表。
 *
 * <p>正式 Element 表就绪后新增 {@code ElementPropositionSource} 并替换注入即可，
 * 校验器与页面不动。排序键固定为 (startOffset, endOffset, id)，与契约的 displayNo 生成规则一致。
 */
@Service
@RequiredArgsConstructor
public class MockPropositionSource implements PropositionSource {

    private final MockPropositionMapper mapper;

    @Override
    public List<PropositionRef> findBySnapshot(String snapshotId) {
        return mapper.selectList(
                        new LambdaQueryWrapper<MockProposition>()
                                .eq(MockProposition::getDraftId, snapshotId))
                .stream()
                .sorted(Comparator.comparing(MockProposition::getStartOffset,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(MockProposition::getEndOffset,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(MockProposition::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(MockPropositionSource::toRef)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, PropositionRef> mapByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, PropositionRef> result = new LinkedHashMap<>();
        mapper.selectList(new LambdaQueryWrapper<MockProposition>().in(MockProposition::getId, ids))
                .forEach(p -> result.put(p.getId(), toRef(p)));
        return result;
    }

    @Override
    public PropositionRef find(String id) {
        MockProposition p = id == null ? null : mapper.selectById(id);
        return p == null ? null : toRef(p);
    }

    private static PropositionRef toRef(MockProposition p) {
        return new PropositionRef(
                p.getId(),
                p.getDraftId(),
                p.getLabelCode(),
                p.getDisplayNo(),
                Boolean.TRUE.equals(p.getIsProposition()),
                p.getText());
    }
}
