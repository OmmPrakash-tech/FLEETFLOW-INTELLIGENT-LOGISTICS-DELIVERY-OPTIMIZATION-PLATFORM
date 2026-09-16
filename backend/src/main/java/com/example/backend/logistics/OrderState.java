package com.example.backend.logistics;

import com.example.backend.common.ApiException;
import java.util.*;

public enum OrderState {
    CREATED,CONFIRMED,ALLOCATING,WAREHOUSE_ASSIGNED,PICKING,PACKED,DISPATCHED,IN_TRANSIT,OUT_FOR_DELIVERY,DELIVERED,CANCELLED,FAILED,RETURN_REQUESTED,RETURNED;
    public boolean allows(OrderState next) {
        return switch(this) {
            case CREATED -> Set.of(CONFIRMED,CANCELLED).contains(next);
            case CONFIRMED -> Set.of(ALLOCATING,CANCELLED).contains(next);
            case ALLOCATING -> Set.of(WAREHOUSE_ASSIGNED,CANCELLED).contains(next);
            case WAREHOUSE_ASSIGNED -> Set.of(PICKING,CANCELLED).contains(next);
            case PICKING -> Set.of(PACKED,CANCELLED).contains(next);
            case PACKED -> Set.of(DISPATCHED,CANCELLED).contains(next);
            case DISPATCHED -> Set.of(IN_TRANSIT,FAILED).contains(next);
            case IN_TRANSIT -> Set.of(OUT_FOR_DELIVERY,FAILED).contains(next);
            case OUT_FOR_DELIVERY -> Set.of(DELIVERED,FAILED).contains(next);
            case DELIVERED -> next==RETURN_REQUESTED;
            case RETURN_REQUESTED,FAILED -> next==RETURNED;
            default -> false;
        };
    }
    public void require(OrderState next) { if(!allows(next)) throw ApiException.conflict("INVALID_TRANSITION","Cannot move from "+this+" to "+next); }
}
