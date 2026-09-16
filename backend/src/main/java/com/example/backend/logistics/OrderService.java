package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import static com.example.backend.common.Store.*;

@Service
public class OrderService {
    private final Store db; private final Idempotency idem; private final MeterRegistry metrics;
    public OrderService(Store db,Idempotency idem,MeterRegistry metrics) { this.db=db; this.idem=idem; this.metrics=metrics; }
    public Map<String,Object> accessible(Actor actor,UUID id,boolean lock) {
        var order=db.one("SELECT * FROM customer_order WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(!actor.staff() && !actor.id().equals(order.get("customer_id"))) {
            if(!actor.role().equals("DRIVER") || db.rows("SELECT s.id FROM shipment s JOIN driver d ON d.id=s.driver_id WHERE s.order_id=? AND d.user_id=?",id,actor.id()).isEmpty()) throw ApiException.forbidden();
        }
        return order;
    }
    @Transactional public UUID create(Actor actor,Requests.Order r,String key) {
        if(!actor.role().equals("CUSTOMER")&&!actor.staff()) throw ApiException.forbidden();
        var timer=io.micrometer.core.instrument.Timer.start(metrics);
        try {
            String payload=r.toString(); UUID existing=idem.existing(actor.id(),key,"order",payload); if(existing!=null) return existing;
            var items=r.items().stream().sorted(Comparator.comparing(Requests.Item::productId)).toList();
            if(items.stream().map(Requests.Item::productId).distinct().count()!=items.size()) throw new ApiException(400,"DUPLICATE_PRODUCT","Combine duplicate product quantities");
            UUID id=UUID.randomUUID(); BigDecimal total=BigDecimal.ZERO;
            db.update("INSERT INTO customer_order(id,customer_id,status,address,latitude,longitude,priority,sla_hours) VALUES(?,?,'CREATED',?,?,?,?,?)",id,actor.id(),r.address(),r.latitude(),r.longitude(),r.priority(),r.slaHours());
            for(var item:items) {
                var product=db.one("SELECT price FROM product WHERE id=?",item.productId()); BigDecimal price=(BigDecimal)product.get("price");
                total=total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
                db.update("INSERT INTO order_item(order_id,product_id,quantity,unit_price) VALUES(?,?,?,?)",id,item.productId(),item.quantity(),price);
            }
            var destination=new Optimization.Point(r.latitude(),r.longitude());
            var candidates=new ArrayList<>(db.rows("SELECT * FROM warehouse WHERE status IN ('ACTIVE','BUSY') AND current_load<capacity"));
            candidates.sort(Comparator.<Map<String,Object>>comparingDouble(w->Optimization.warehouseScore(Optimization.distance(destination,point(w)),integer(w,"current_load"),integer(w,"capacity"),r.priority(),r.slaHours())).thenComparing(w->w.get("id").toString()));
            UUID chosen=null;
            // Lock warehouses in a fixed order to prevent allocation deadlocks while retaining ranked selection.
            var locked=new HashMap<UUID,Map<String,Object>>();
            candidates.stream().map(w->id(w,"id")).sorted().forEach(w->locked.put(w,db.one("SELECT * FROM warehouse WHERE id=? FOR UPDATE",w)));
            for(var candidate:candidates) {
                UUID warehouse=id(candidate,"id"); var current=locked.get(warehouse);
                if(!Set.of("ACTIVE","BUSY").contains(current.get("status")) || integer(current,"current_load")>=integer(current,"capacity")) continue;
                boolean sufficient=true;
                for(var item:items) {
                    var stock=db.rows("SELECT quantity-reserved AS available FROM inventory WHERE warehouse_id=? AND product_id=? FOR UPDATE",warehouse,item.productId());
                    if(stock.isEmpty()||integer(stock.getFirst(),"available")<item.quantity()) { sufficient=false; break; }
                }
                if(sufficient) { chosen=warehouse; break; }
            }
            if(chosen==null) { metrics.counter("fleetflow.inventory.conflicts").increment(); throw ApiException.conflict("INSUFFICIENT_INVENTORY","No available warehouse can fulfill every item"); }
            for(var item:items) {
                db.update("UPDATE inventory SET reserved=reserved+? WHERE warehouse_id=? AND product_id=?",item.quantity(),chosen,item.productId());
                var stock=db.one("SELECT quantity-reserved AS available,low_stock_threshold FROM inventory WHERE warehouse_id=? AND product_id=?",chosen,item.productId());
                if(integer(stock,"available")<=integer(stock,"low_stock_threshold")) db.alert("LOW_INVENTORY",chosen+":"+item.productId(),"Available stock is at or below the reorder threshold");
            }
            db.update("UPDATE warehouse SET current_load=current_load+1 WHERE id=?",chosen);
            for(String state:List.of("CONFIRMED","ALLOCATING","WAREHOUSE_ASSIGNED")) { db.update("UPDATE customer_order SET status=?,warehouse_id=?,total=?,updated_at=now() WHERE id=?",state,chosen,total,id); db.audit(actor.id(),"ORDER_"+state,id); }
            db.event(id,"OrderCreated"); db.event(id,"InventoryReserved"); db.event(id,"WarehouseAssigned"); db.audit(actor.id(),"ORDER_CREATED",id);
            idem.save(actor.id(),key,"order",payload,id); return id;
        } finally { timer.stop(metrics.timer("fleetflow.order.processing")); }
    }
    public static Optimization.Point point(Map<String,Object> row) { return new Optimization.Point(number(row,"latitude"),number(row,"longitude")); }
    @Transactional public UUID assign(Actor actor,UUID orderId,String key) {
        actor.requireStaff(); UUID previous=idem.existing(actor.id(),key,"assign",orderId.toString()); if(previous!=null) return previous;
        var order=accessible(actor,orderId,true);
        if(!order.get("status").equals("PACKED")) throw ApiException.conflict("INVALID_TRANSITION","Pack the order before assigning a driver");
        if(!db.rows("SELECT id FROM shipment WHERE order_id=?",orderId).isEmpty()) throw ApiException.conflict("ALREADY_ASSIGNED","Order already has a shipment");
        var warehouse=db.one("SELECT * FROM warehouse WHERE id=?",order.get("warehouse_id"));
        double weight=number(db.one("SELECT SUM(i.quantity*p.weight_kg) AS weight FROM order_item i JOIN product p ON p.id=i.product_id WHERE order_id=?",orderId),"weight");
        var drivers=db.rows("SELECT d.*,v.capacity_kg,v.type FROM driver d JOIN vehicle v ON v.id=d.vehicle_id LEFT JOIN app_user u ON u.id=d.user_id WHERE d.status='AVAILABLE' AND d.workload=0 AND v.status='AVAILABLE' AND v.capacity_kg>=? AND (d.user_id IS NULL OR (u.active=true AND u.role='DRIVER')) ORDER BY d.id FOR UPDATE OF d,v SKIP LOCKED",weight);
        var best=drivers.stream().min(Comparator.<Map<String,Object>>comparingDouble(d->Optimization.driverScore(Optimization.distance(point(d),point(warehouse)),weight,number(d,"capacity_kg"),integer(d,"workload"),integer(order,"priority"),integer(order,"sla_hours"))).thenComparing(d->d.get("id").toString())).orElseThrow(()->ApiException.conflict("NO_DRIVER","No available driver with sufficient vehicle capacity"));
        UUID driverId=id(best,"id"),shipmentId=UUID.randomUUID(),routeId=UUID.randomUUID();
        double km=Optimization.distance(point(warehouse),point(order));
        var graph=Map.of("warehouse",List.of(new Optimization.Edge("destination",km)),"destination",List.<Optimization.Edge>of());
        var started=io.micrometer.core.instrument.Timer.start(metrics); var path=Optimization.dijkstra(graph,"warehouse","destination"); started.stop(metrics.timer("fleetflow.route.computation"));
        double speed=best.get("type").equals("BIKE")?25:35; int minutes=Optimization.etaMinutes(path.distance(),speed,1,1);
        String nodes="[["+number(warehouse,"latitude")+","+number(warehouse,"longitude")+"],["+number(order,"latitude")+","+number(order,"longitude")+"]]";
        db.update("INSERT INTO route(id,nodes_json,distance_km,duration_minutes) VALUES(?,?,?,?)",routeId,nodes,km,minutes);
        db.update("INSERT INTO shipment(id,order_id,warehouse_id,driver_id,route_id,status,eta) VALUES(?,?,?,?,?,'READY_FOR_DISPATCH',?)",shipmentId,orderId,order.get("warehouse_id"),driverId,routeId,java.sql.Timestamp.from(Instant.now().plusSeconds(minutes*60L)));
        db.update("UPDATE driver SET status='ASSIGNED',workload=1 WHERE id=?",driverId); db.update("UPDATE vehicle SET status='ASSIGNED' WHERE id=?",best.get("vehicle_id"));
        event(shipmentId,"READY_FOR_DISPATCH","Driver assigned; route uses a geodesic estimate"); db.audit(actor.id(),"DRIVER_ASSIGNED",shipmentId); db.event(orderId,"DriverAssigned");
        idem.save(actor.id(),key,"assign",orderId.toString(),shipmentId); return shipmentId;
    }
    @Transactional public void transition(Actor actor,UUID orderId,OrderState next,String key) {
        String operation="transition:"+orderId,payload=next.name(); if(idem.existing(actor.id(),key,operation,payload)!=null) return;
        var order=accessible(actor,orderId,true); var current=OrderState.valueOf(order.get("status").toString());
        if(!actor.staff()) {
            if(actor.role().equals("CUSTOMER") && next!=OrderState.CANCELLED && next!=OrderState.RETURN_REQUESTED) throw ApiException.forbidden();
            if(actor.role().equals("DRIVER") && !Set.of(OrderState.IN_TRANSIT,OrderState.OUT_FOR_DELIVERY,OrderState.DELIVERED,OrderState.FAILED).contains(next)) throw ApiException.forbidden();
        }
        current.require(next);
        var shipments=db.rows("SELECT * FROM shipment WHERE order_id=? FOR UPDATE",orderId);
        if(next==OrderState.DISPATCHED && shipments.isEmpty()) throw ApiException.conflict("DRIVER_REQUIRED","Assign a driver before dispatch");
        boolean finish=Set.of(OrderState.DELIVERED,OrderState.CANCELLED,OrderState.RETURNED).contains(next);
        if(finish) {
            UUID warehouse=id(order,"warehouse_id"); db.one("SELECT id FROM warehouse WHERE id=? FOR UPDATE",warehouse);
            for(var item:db.rows("SELECT * FROM order_item WHERE order_id=? ORDER BY product_id",orderId)) {
                int quantity=integer(item,"quantity");
                if(next==OrderState.RETURNED && current==OrderState.RETURN_REQUESTED) db.update("UPDATE inventory SET quantity=quantity+? WHERE warehouse_id=? AND product_id=?",quantity,warehouse,item.get("product_id"));
                else db.update("UPDATE inventory SET reserved=reserved-?,quantity=quantity-? WHERE warehouse_id=? AND product_id=?",quantity,next==OrderState.DELIVERED?quantity:0,warehouse,item.get("product_id"));
            }
            if(current!=OrderState.RETURN_REQUESTED) db.update("UPDATE warehouse SET current_load=current_load-1 WHERE id=?",warehouse);
        }
        db.update("UPDATE customer_order SET status=?,updated_at=now(),delivered_at=CASE WHEN ?='DELIVERED' THEN now() ELSE delivered_at END WHERE id=?",next.name(),next.name(),orderId);
        if(!shipments.isEmpty() && next!=OrderState.RETURN_REQUESTED) {
            var shipment=shipments.getFirst(); UUID shipmentId=id(shipment,"id");
            String status=next==OrderState.CANCELLED?"FAILED":next.name();
            if(Set.of("DISPATCHED","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","RETURNED").contains(status)) {
                db.update("UPDATE shipment SET status=?,updated_at=now(),delivered_at=CASE WHEN ?='DELIVERED' THEN now() ELSE delivered_at END WHERE id=?",status,status,shipmentId);
                event(shipmentId,status,next==OrderState.CANCELLED?"Order cancelled before dispatch":"Order moved to "+status);
                if(next==OrderState.DISPATCHED) db.update("UPDATE driver SET status='ON_DELIVERY' WHERE id=?",shipment.get("driver_id"));
                if(finish && current!=OrderState.RETURN_REQUESTED) {
                    var driver=db.one("SELECT * FROM driver WHERE id=? FOR UPDATE",shipment.get("driver_id"));
                    db.update("UPDATE driver SET status='AVAILABLE',workload=0 WHERE id=?",driver.get("id")); db.update("UPDATE vehicle SET status='AVAILABLE' WHERE id=?",driver.get("vehicle_id"));
                }
                if(next==OrderState.FAILED) db.alert("FAILED_DELIVERY",shipmentId,"Delivery failed; return stock before releasing assignment");
                db.event(orderId,"ShipmentStatusChanged");
            }
        }
        db.audit(actor.id(),"ORDER_"+next,orderId); db.event(orderId,next==OrderState.DELIVERED?"DeliveryCompleted":next==OrderState.CANCELLED?"OrderCancelled":"OrderStatusChanged");
        idem.save(actor.id(),key,operation,payload,orderId);
    }
    private void event(UUID shipment,String status,String detail) { db.update("INSERT INTO shipment_event(shipment_id,status,detail) VALUES(?,?,?)",shipment,status,detail); }
}
