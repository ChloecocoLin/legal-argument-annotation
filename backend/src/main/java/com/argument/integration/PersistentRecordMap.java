package com.argument.integration;

import com.argument.version.VersionJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Compatibility facade for A's map-based services. Every mutation joins the caller's JDBC transaction. */
public final class PersistentRecordMap extends AbstractMap<String, Map<String,Object>> {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String kind;
    public PersistentRecordMap(JdbcTemplate jdbc,ObjectMapper mapper,String kind){this.jdbc=jdbc;this.mapper=mapper;this.kind=kind;}
    @Override public Map<String,Object> get(Object key){
        if(!(key instanceof String)) return null;
        var rows=jdbc.queryForList("SELECT payload_json FROM workflow_records WHERE object_kind=? AND object_id=?",String.class,kind,key);
        if(rows.isEmpty())return null;
        var values=mapper.convertValue(VersionJson.parse(mapper,rows.get(0)),new TypeReference<Map<String,Object>>(){});
        return new RecordView((String)key,values);
    }
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public Map<String,Object> put(String id,Map<String,Object> value){
        writable();VersionJson.uuid(id);if(!id.equals(value.get("id")))throw new IllegalArgumentException("Record identity mismatch");
        var old=get(id);String json=mapper.valueToTree(value).toString();
        jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE payload_json=?",kind,id,json,json);
        return old;
    }
    @Override public Map<String,Object> remove(Object key){writable();var old=get(key);jdbc.update("DELETE FROM workflow_records WHERE object_kind=? AND object_id=?",kind,key);return old;}
    @Override public Set<Entry<String,Map<String,Object>>> entrySet(){
        var result=new LinkedHashSet<Entry<String,Map<String,Object>>>();
        for(String id:jdbc.queryForList("SELECT object_id FROM workflow_records WHERE object_kind=? ORDER BY object_id",String.class,kind))result.add(new SimpleImmutableEntry<>(id,get(id)));
        return Collections.unmodifiableSet(result);
    }
    private void writable(){if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Workflow writes require a shared transaction");}
    private final class RecordView extends AbstractMap<String,Object> {
        private final String id;private final Map<String,Object> values;
        RecordView(String id,Map<String,Object> values){this.id=id;this.values=values;}
        @Override public Object get(Object key){return values.get(key);}
        @Override public boolean containsKey(Object key){return values.containsKey(key);}
        @Override public Object put(String key,Object value){writable();if("id".equals(key)&&!id.equals(value))throw new IllegalArgumentException("Record identity is immutable");var old=values.put(key,value);PersistentRecordMap.this.put(id,values);return old;}
        @Override public Object remove(Object key){writable();if("id".equals(key))throw new IllegalArgumentException("Record identity is immutable");var old=values.remove(key);PersistentRecordMap.this.put(id,values);return old;}
        @Override public Set<Entry<String,Object>> entrySet(){return Collections.unmodifiableMap(values).entrySet();}
    }
}
