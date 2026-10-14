package com.argument.version.local;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.VersionJson;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !prod")
public class LocalAnnotationDependencies implements AnnotationDependencies {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LocalAnnotationDependencies(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public ObjectNode read(String kind, String id) { return select(kind,id,false); }
    public ObjectNode lock(String kind, String id) { return select(kind,id,true); }
    private ObjectNode select(String kind, String id, boolean lock) {
        List<String> rows=jdbc.queryForList("SELECT payload_json FROM local_annotation_dependencies WHERE object_kind=? AND object_id=?"+(lock?" FOR UPDATE":""),String.class,kind,id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "找不到所请求的数据");
        return VersionJson.parse(mapper,rows.get(0));
    }
    public List<ObjectNode> find(String kind,String field,String value) {
        return jdbc.queryForList("SELECT payload_json FROM local_annotation_dependencies WHERE object_kind=? ORDER BY object_id",String.class,kind)
            .stream().map(s->VersionJson.parse(mapper,s)).filter(n->field==null || n.path(field).asText().equals(value)).toList();
    }
    public void replace(String kind,ObjectNode record) {
        if(jdbc.update("UPDATE local_annotation_dependencies SET payload_json=? WHERE object_kind=? AND object_id=?",record.toString(),kind,record.path("id").asText())!=1)
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    public boolean referencedByAdjudication(String versionId) {
        return find("session",null,null).stream().anyMatch(s->{ for(var id:s.path("sourceVersionIds")) if(id.asText().equals(versionId)) return true; return false; });
    }
}
