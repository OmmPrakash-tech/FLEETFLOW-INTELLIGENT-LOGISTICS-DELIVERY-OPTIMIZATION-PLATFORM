package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.AuthService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Component
public class Idempotency {
    private final Store db;
    public Idempotency(Store db) { this.db=db; }
    public UUID existing(UUID actor,String key,String operation,String payload) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{8,100}")) throw new ApiException(400,"INVALID_IDEMPOTENCY_KEY","Provide an Idempotency-Key of 8–100 letters, numbers, dots, colons, underscores or hyphens");
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Idempotency requires a transaction");
        db.rows("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",actor+":"+operation+":"+key);
        var rows=db.rows("SELECT fingerprint,resource_id FROM idempotency WHERE actor_id=? AND request_key=? AND operation=?",actor,key,operation);
        if(rows.isEmpty()) return null;
        if(!rows.getFirst().get("fingerprint").equals(AuthService.hash(payload))) throw ApiException.conflict("IDEMPOTENCY_MISMATCH","This key was used for a different request");
        return Store.id(rows.getFirst(),"resource_id");
    }
    public void save(UUID actor,String key,String operation,String payload,UUID resource) {
        db.update("INSERT INTO idempotency(actor_id,request_key,operation,fingerprint,resource_id) VALUES(?,?,?,?,?)",actor,key,operation,AuthService.hash(payload),resource);
    }
}
