package com.argument.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import db.migration.common.V10__shared_workflow_store;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedStorePersistenceTest {
    @TempDir Path directory;
    DriverManagerDataSource source(String name) {
        return new DriverManagerDataSource("jdbc:h2:file:"+directory.resolve(name).toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
    }
    @Test void taskSurvivesDatabaseShutdownAndRollbackDoesNotPersistPartialChanges() {
        var ds=source("restart");var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE workflow_records(object_kind VARCHAR(64),object_id VARCHAR(36),payload_json LONGTEXT,PRIMARY KEY(object_kind,object_id))");
        var records=new PersistentRecordMap(jdbc,new ObjectMapper(),"task");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));String id=UUID.randomUUID().toString();
        assertThatThrownBy(()->records.put(id,Map.of("id",id))).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status->records.put(id,Map.of("id",id,"name","重启保留任务")));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{records.get(id).put("name","不应保存");throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        jdbc.execute("SHUTDOWN");
        var reopened=new PersistentRecordMap(new JdbcTemplate(source("restart")),new ObjectMapper(),"task");
        assertThat(reopened.get(id).get("name")).isEqualTo("重启保留任务");
    }
    @Test void migrationImportsExistingRecordsAndIndexesSnapshotsWithoutRewritingThem() throws Exception {
        var ds=source("legacy");var jdbc=new JdbcTemplate(ds);String id=UUID.randomUUID().toString(),element=UUID.randomUUID().toString();
        jdbc.execute("CREATE TABLE local_annotation_dependencies(object_kind VARCHAR(64),object_id VARCHAR(36),payload_json LONGTEXT)");
        jdbc.execute("CREATE TABLE annotation_version_snapshots(snapshot_json LONGTEXT)");
        jdbc.execute("CREATE TABLE final_annotation_versions(snapshot_json LONGTEXT)");
        String record="{\"id\":\""+id+"\",\"recordVersion\":7}",snapshot="{\"version\":{\"id\":\""+id+"\"},\"elements\":[{\"id\":\""+element+"\"}]}";
        jdbc.update("INSERT INTO local_annotation_dependencies VALUES('task',?,?)",id,record);
        jdbc.update("INSERT INTO annotation_version_snapshots VALUES(?)",snapshot);
        try(var connection=ds.getConnection()) {var context=mock(Context.class);when(context.getConnection()).thenReturn(connection);new V10__shared_workflow_store().migrate(context);}
        assertThat(jdbc.queryForObject("SELECT payload_json FROM workflow_records WHERE object_id=?",String.class,id)).isEqualTo(record);
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM annotation_version_snapshots",String.class)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM snapshot_object_owners WHERE object_id=?",String.class,element)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT payload_json FROM local_annotation_dependencies",String.class)).isEqualTo(record);
    }
}
