package com.argument.version.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("(local | test) & !prod")
public class LocalFixtureLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LocalFixtureLoader(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        // A persistent local database is never reset by starting the application.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM local_annotation_dependencies",Integer.class)>0)return;
        try(InputStream input=new ClassPathResource("fixtures/member-d-ready-submission.json").getInputStream()) {
            var fixture=mapper.readTree(input);
            for(var entry:fixture.path("records"))jdbc.update("INSERT INTO local_annotation_dependencies(object_kind,object_id,payload_json) VALUES (?,?,?)",entry.path("kind").asText(),entry.path("value").path("id").asText(),entry.path("value").toString());
        }
    }
}
