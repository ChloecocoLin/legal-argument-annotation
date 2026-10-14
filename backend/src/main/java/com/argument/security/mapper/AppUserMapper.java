package com.argument.security.mapper;

import com.argument.security.entity.AppUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 账号与角色查询。归成员 C（分工.md §六）。
 *
 * <p>只读：本轮不做账号与角色的管理界面（那属于「用户管理」，不在 §六 的 10–15% 里）。
 * 账号与角色由 {@code data.sql} 种子给出。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 取账号的角色代码（admin / annotator / arbiter / expert / student / teacher）。
     *
     * <p>排序是为了让同一账号的权限集合在日志与响应里顺序稳定。
     */
    @Select("SELECT r.code FROM app_role r "
            + "JOIN app_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} ORDER BY r.code")
    List<String> selectRoleCodes(@Param("userId") String userId);
}
