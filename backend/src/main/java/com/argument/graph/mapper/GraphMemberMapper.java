package com.argument.graph.mapper;

import com.argument.graph.entity.GraphMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GraphMemberMapper extends BaseMapper<GraphMember> {

    /** 重算成员前清空；graph_member 无软删列，直接物理删除。 */
    @Delete("DELETE FROM graph_member WHERE graph_id = #{graphId}")
    int deleteByGraphId(@Param("graphId") String graphId);
}
