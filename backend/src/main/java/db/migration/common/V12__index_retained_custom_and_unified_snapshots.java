package db.migration.common;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.*;

/** Backfill only derived lookup rows. Retained custom/unified content is never rewritten. */
public class V12__index_retained_custom_and_unified_snapshots extends BaseJavaMigration {
    @Override public void migrate(Context context)throws Exception{
        var connection=context.getConnection();var mapper=new ObjectMapper();
        try(var statement=connection.prepareStatement("SELECT object_kind,object_id,payload_json FROM workflow_records WHERE object_kind IN ('custom','unifiedSnapshot')");var rows=statement.executeQuery()){
            while(rows.next()){
                String type="custom".equals(rows.getString(1))?"ARBITER_CUSTOM":"UNIFIED_PROPOSITION";String owner=rows.getString(2);var snapshot=mapper.readTree(rows.getString(3));
                for(String collection:new String[]{"elements","relations","participants"})for(var object:snapshot.path(collection)){
                    try(var exists=connection.prepareStatement("SELECT COUNT(*) FROM snapshot_object_owners WHERE object_id=?")){exists.setString(1,object.path("id").asText());try(var count=exists.executeQuery()){count.next();if(count.getInt(1)>0)continue;}}
                    try(var insert=connection.prepareStatement("INSERT INTO snapshot_object_owners(object_id,owner_type,owner_id,object_kind) VALUES(?,?,?,?)")){insert.setString(1,object.path("id").asText());insert.setString(2,type);insert.setString(3,owner);insert.setString(4,collection);insert.executeUpdate();}
                }
            }
        }
    }
}
