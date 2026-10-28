package com.argument.graph.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 论证图成员：元素或关系。归成员 C。表无软删列，整体重算时物理替换。 */
@Data
@TableName("graph_member")
public class GraphMember {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String graphId;

    /** ELEMENT / RELATION。 */
    private String targetKind;

    private String targetId;
}
