package com.example.backend.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Explicit SQL repository: values are always bound parameters, never interpolated client input. */
@Repository
public class Store {
    private final JdbcTemplate jdbc;
    public Store(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ApiException(404, "NOT_FOUND", "Resource not found");
        return rows.getFirst();
    }
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public void audit(UUID actor, String action, Object resource) { update("INSERT INTO audit_log(actor_id,action,resource_id) VALUES(?,?,?)",actor,action,resource.toString()); }
    public void event(UUID aggregate, String type) { update("INSERT INTO outbox_event(aggregate_id,type) VALUES(?,?)",aggregate,type); }
    public void alert(String type, Object id, String message) {
        update("INSERT INTO alert(type,resource_id,message) VALUES(?,?,?) ON CONFLICT(type,resource_id) WHERE NOT resolved DO NOTHING",type,id.toString(),message);
    }
    public static UUID id(Map<String,Object> row, String key) { return (UUID)row.get(key); }
    public static double number(Map<String,Object> row, String key) { return ((Number)row.get(key)).doubleValue(); }
    public static int integer(Map<String,Object> row, String key) { return ((Number)row.get(key)).intValue(); }
}
