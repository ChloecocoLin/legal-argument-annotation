package com.argument.integration;

import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/** Shared filtered reads; does not change ownership, transaction or locking semantics. */
public final class WorkflowRecordQuery {
    private WorkflowRecordQuery() {}
    public static List<ObjectNode> find(JdbcTemplate jdbc,ObjectMapper mapper,String kind,String field,String value) {
        if("id".equals(field))return jdbc.queryForList("SELECT payload_json FROM workflow_records WHERE object_kind=? AND object_id=?",String.class,kind,value).stream().map(s->VersionJson.parse(mapper,s)).toList();
        // Narrow results in SQL before transferring and parsing payloads; all input is bound.
        String sql="SELECT payload_json FROM workflow_records WHERE object_kind=?";
        var args=new java.util.ArrayList<Object>();args.add(kind);
        if(field!=null && value!=null && field.endsWith("Id")){sql+=" AND payload_json REGEXP ?";args.add(java.util.regex.Pattern.quote(mapper.valueToTree(field).toString())+"\\s*:\\s*"+java.util.regex.Pattern.quote(mapper.valueToTree(value).toString()));}
        return jdbc.queryForList(sql+" ORDER BY object_id",String.class,args.toArray()).stream().map(s->VersionJson.parse(mapper,s)).filter(n->field==null || n.path(field).asText().equals(value)).toList();
    }
}
