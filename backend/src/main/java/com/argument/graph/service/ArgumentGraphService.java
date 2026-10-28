package com.argument.graph.service;

import com.argument.graph.dto.GraphValidateRequest;
import com.argument.graph.dto.GraphValidateResult;
import com.argument.graph.dto.GraphView;
import com.argument.graph.dto.LayoutCreateRequest;

/** 论证图服务（GR01–GR03），归成员 C。 */
public interface ArgumentGraphService {

    /** GR01 取 owner 的论证图（连带节点、连线、坐标与问题清单）。 */
    GraphView getGraph(String ownerType, String ownerId, Integer layoutVersion);

    /** GR02 审查一份图示内容（工作视图 / 正式视图提交前自检）。 */
    GraphValidateResult validateDraftGraph(String draftId, GraphValidateRequest request);

    /** GR03 保存坐标，返回新的布局版本号。只写坐标，不改关系。 */
    int saveLayout(String graphId, LayoutCreateRequest request);
}
