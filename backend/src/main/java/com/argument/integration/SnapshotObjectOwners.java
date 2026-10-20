package com.argument.integration;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
/** Derived lookup only. The immutable snapshot remains the sole content authority. */
public final class SnapshotObjectOwners {
 private SnapshotObjectOwners(){}
 public static void index(JdbcTemplate jdbc,JsonNode snapshot,String type){String owner=snapshot.path("version").path("id").asText();for(String collection:new String[]{"elements","relations","participants"})for(var object:snapshot.path(collection))jdbc.update("INSERT INTO snapshot_object_owners(object_id,owner_type,owner_id,object_kind) VALUES(?,?,?,?)",object.path("id").asText(),type,owner,collection);}
}
