package com.argument.integration;

import com.argument.version.local.LocalAnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class WorkflowRecordQueryTest {
    @Test void filtersIdentityAndRelatedRecordsWithoutLosingWhitespaceOrScope() throws Exception {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE workflow_records(object_kind VARCHAR(64),object_id VARCHAR(36),payload_json LONGTEXT,PRIMARY KEY(object_kind,object_id))");
        var mapper=new ObjectMapper();var data=new LocalAnnotationDependencies(jdbc,mapper);
        String id=UUID.randomUUID().toString(), other=UUID.randomUUID().toString(), task=UUID.randomUUID().toString();
        var row=mapper.createObjectNode().put("id",id).put("taskDocumentId",task).put("count",2);
        jdbc.update("INSERT INTO workflow_records VALUES(?,?,?)","segmentationSubmission",id,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(row));
        jdbc.update("INSERT INTO workflow_records VALUES(?,?,?)","segmentationSubmission",other,mapper.createObjectNode().put("id",other).put("taskDocumentId",other).toString());
        jdbc.update("INSERT INTO workflow_records VALUES(?,?,?)","draft",id,row.toString());
        assertThat(data.find("segmentationSubmission","taskDocumentId",task)).containsExactly(row);
        assertThat(data.find("segmentationSubmission","id",id)).containsExactly(row);
        assertThat(data.find("segmentationSubmission","id",task)).isEmpty();
        assertThat(data.find("segmentationSubmission","count","2")).containsExactly(row);
        assertThat(data.find("segmentationSubmission","taskDocumentId",task+".*")).isEmpty();
        assertThat(data.find("segmentationSubmission",null,null)).hasSize(2);
        assertThat(new PersistentRecordMap(jdbc,mapper,"segmentationSubmission").find("taskDocumentId",task)).hasSize(1);
    }
}
