package com.argument.adjudication.service;

import com.argument.adjudication.dto.RelationAdjudicationItemView;
import com.argument.adjudication.dto.RelationAdjudicationResultView;
import com.argument.adjudication.dto.RelationResultRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** 关系差异与裁定服务（J06 + 关系裁定结果试算），归成员 C。 */
public interface RelationAdjudicationService {

    /**
     * J06 列出关系差异和一致项（分页）。
     *
     * <p>命题阶段未冻结时抛 {@code PROPOSITIONS_NOT_COMPLETE}——「先裁定命题、后裁定关系」是硬顺序。
     * 会话尚无裁定项时按需建立（幂等）：这是 C 的差异算法落地的那一步
     * （《核心标注与裁定数据模型》§1 处理次序 4）。
     */
    IPage<RelationAdjudicationItemView> listItems(
            String sessionId, String status, String conflictType, int page, int size);

    /** 生成关系裁定结果（只读试算：不写库，交付物本身）。 */
    RelationAdjudicationResultView resolveResult(String sessionId, RelationResultRequest request);
}
