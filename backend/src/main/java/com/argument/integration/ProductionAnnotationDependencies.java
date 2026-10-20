package com.argument.integration;
import com.argument.version.local.LocalAnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Shared JDBC implementation only; does not load a fixture or a development identity in production. */
@Component @Profile("prod")
public class ProductionAnnotationDependencies extends LocalAnnotationDependencies {
 public ProductionAnnotationDependencies(JdbcTemplate jdbc,ObjectMapper mapper){super(jdbc,mapper);}
}
