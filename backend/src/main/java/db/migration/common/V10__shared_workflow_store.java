package db.migration.common;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve existing D local data; new databases are seeded only by the explicit local/test loader. */
public class V10__shared_workflow_store extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE workflow_records (object_kind VARCHAR(64) NOT NULL, object_id VARCHAR(36) NOT NULL, payload_json LONGTEXT NOT NULL, PRIMARY KEY(object_kind,object_id))");
            statement.execute("CREATE TABLE snapshot_object_owners (object_id VARCHAR(36) PRIMARY KEY, owner_type VARCHAR(32) NOT NULL, owner_id VARCHAR(36) NOT NULL, object_kind VARCHAR(32) NOT NULL)");
            statement.execute("CREATE INDEX idx_snapshot_object_owner ON snapshot_object_owners(owner_type,owner_id)");
            statement.execute("CREATE TABLE auth_accounts (user_id VARCHAR(36) PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL)");
        }
        boolean legacy = false;
        try (var tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) if ("local_annotation_dependencies".equalsIgnoreCase(tables.getString("TABLE_NAME"))) legacy = true;
        }
        if (legacy) try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO workflow_records(object_kind,object_id,payload_json) SELECT object_kind,object_id,payload_json FROM local_annotation_dependencies");
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String[] source : new String[][]{{"annotation_version_snapshots","ANNOTATION_VERSION"},{"final_annotation_versions","FINAL_VERSION"}}) {
            try(var query=connection.createStatement();var rows=query.executeQuery("SELECT snapshot_json FROM "+source[0])) {
                while(rows.next()) {
                    var snapshot=mapper.readTree(rows.getString(1));String owner=snapshot.path("version").path("id").asText();
                    for(String kind:new String[]{"elements","relations","participants"})for(var object:snapshot.path(kind))try(var insert=connection.prepareStatement("INSERT INTO snapshot_object_owners(object_id,owner_type,owner_id,object_kind) VALUES(?,?,?,?)")){
                        insert.setString(1,object.path("id").asText());insert.setString(2,source[1]);insert.setString(3,owner);insert.setString(4,kind);insert.executeUpdate();
                    }
                }
            }
        }
    }
}
