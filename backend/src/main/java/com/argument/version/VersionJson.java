package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

public final class VersionJson {
    private VersionJson() {}
    public static String uuid() { return UUID.randomUUID().toString(); }
    public static String uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ID 必须为规范 UUID v4");
        return value;
    }
    public static String now() { return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(); }
    public static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static ObjectNode parse(ObjectMapper mapper, String json) {
        try { return (ObjectNode) mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored snapshot", e); }
    }
    public static String str(JsonNode object, String field) { return object.path(field).asText(); }
    public static void stamp(ObjectNode object, String actor, String time) {
        object.put("id", uuid()); object.put("createdAt", time); object.put("updatedAt", time);
        object.put("createdById", actor); object.put("updatedById", actor); object.put("recordVersion", 1);
    }
    /** Stable object-key order; array order remains meaningful. */
    public static com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode n) {
        if(n.isObject()){var o=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();var keys=new java.util.TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);keys.forEach(k->o.set(k,canonical(n.get(k))));return o;}if(n.isArray()){var a=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();n.forEach(v->a.add(canonical(v)));return a;}return n;
    }
    public static String canonicalHash(com.fasterxml.jackson.databind.JsonNode n){return sha256(canonical(n).toString());}
}
