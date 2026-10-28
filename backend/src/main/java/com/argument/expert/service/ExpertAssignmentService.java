package com.argument.expert.service;

import com.argument.expert.provider.CurrentUserProvider;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 专家指派校验。local 实现维护会话与演示专家的映射，不把判断散落在 Controller 或业务 Service。
 * 后续接入真正的专家指派表时，只替换这里的数据源。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
public class ExpertAssignmentService {

    // 会话 id -> 被指派的专家 id 集合
    private static final Map<String, Set<String>> SESSION_EXPERTS =
            Map.of(
                    "aba39933-394e-41e0-aa76-1cf8acc57ba7",
                    Set.of(CurrentUserProvider.DEMO_EXPERT_ID));

    public boolean isAssigned(String sessionId, String expertUserId) {
        Set<String> experts = SESSION_EXPERTS.get(sessionId);
        return experts != null && experts.contains(expertUserId);
    }
}
