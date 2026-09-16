package com.example.backend;

import com.example.backend.common.*;
import com.example.backend.security.*;
import com.example.backend.logistics.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @DirtiesContext
class LogisticsIntegrationTests extends PostgresTestDatabase {
    @Autowired Store db; @Autowired CatalogService catalog; @Autowired OrderService orders; @Autowired AuthService auth;
    Actor admin,customer; UUID product,warehouse;
    String key() { return UUID.randomUUID().toString(); }
    @BeforeEach void setup() {
        UUID adminId=UUID.randomUUID(),customerId=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,name,password_hash,role) VALUES(?,?,'Test operator','unused','ADMIN'),(?,?,'Test customer','unused','CUSTOMER')",adminId,key()+"@test.invalid",customerId,key()+"@test.invalid");
        admin=new Actor(adminId,"ADMIN"); customer=new Actor(customerId,"CUSTOMER");
        product=catalog.product(admin,new Requests.Product(key(),"Integration parcel",BigDecimal.ONE,BigDecimal.TEN));
        warehouse=catalog.warehouse(admin,new Requests.Warehouse(key(),"Test depot",20,85,100,Requests.WarehouseStatus.ACTIVE));
        catalog.stock(admin,new Requests.Stock(warehouse,product,5),key());
    }
    Requests.Order request(int quantity) { return new Requests.Order("Test destination",20.2,85.2,2,24,List.of(new Requests.Item(product,quantity))); }
    UUID order(int quantity) { return orders.create(customer,request(quantity),key()); }
    void advance(UUID id,OrderState state) { orders.transition(admin,id,state,key()); }
    void packed(UUID id) { advance(id,OrderState.PICKING); advance(id,OrderState.PACKED); }
    UUID driver(int capacity) {
        UUID vehicle=catalog.vehicle(admin,new Requests.Vehicle(key(),Requests.VehicleType.VAN,BigDecimal.valueOf(capacity)));
        return catalog.driver(admin,new Requests.Driver("Test driver",null,vehicle,20,85));
    }
    @Test void duplicateOrdersAndMismatchedPayload() {
        String key=key(); UUID first=orders.create(customer,request(2),key); assertEquals(first,orders.create(customer,request(2),key));
        assertThrows(ApiException.class,()->orders.create(customer,request(3),key));
        assertEquals(2,Store.integer(db.one("SELECT reserved FROM inventory WHERE warehouse_id=? AND product_id=?",warehouse,product),"reserved"));
    }
    @Test void finalUnitsHaveOnlyOneConcurrentWinner() throws Exception {
        var gate=new CountDownLatch(1); try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<2;i++) attempts.add(pool.submit(()->{ gate.await(); try { order(5); return true; } catch(ApiException e) { assertEquals("INSUFFICIENT_INVENTORY",e.code); return false; } }));
            gate.countDown(); int winners=0; for(var attempt:attempts) if(attempt.get(20,TimeUnit.SECONDS)) winners++;
            assertEquals(1,winners); assertEquals(5,Store.integer(db.one("SELECT reserved FROM inventory WHERE warehouse_id=? AND product_id=?",warehouse,product),"reserved"));
        }
    }
    @Test void concurrentDuplicateRequestsReturnSameOrder() throws Exception {
        String key=key(); var gate=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->{gate.await();return orders.create(customer,request(2),key);});
            var b=pool.submit(()->{gate.await();return orders.create(customer,request(2),key);}); gate.countDown();
            assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    @Test void deliveryFinalizesInventoryAndDeduplicatesEvents() {
        UUID id=order(3); packed(id); driver(10); UUID shipment=orders.assign(admin,id,key());
        for(var state:List.of(OrderState.DISPATCHED,OrderState.IN_TRANSIT,OrderState.OUT_FOR_DELIVERY)) advance(id,state);
        String eventKey=key(); orders.transition(admin,id,OrderState.DELIVERED,eventKey); orders.transition(admin,id,OrderState.DELIVERED,eventKey);
        var stock=db.one("SELECT quantity,reserved FROM inventory WHERE warehouse_id=? AND product_id=?",warehouse,product);
        assertEquals(2,Store.integer(stock,"quantity")); assertEquals(0,Store.integer(stock,"reserved"));
        assertEquals(1,Store.integer(db.one("SELECT COUNT(*) AS count FROM shipment_event WHERE shipment_id=? AND status='DELIVERED'",shipment),"count"));
        assertFalse(db.rows("SELECT id FROM audit_log WHERE resource_id=?",id.toString()).isEmpty());
    }
    @Test void cancelReleasesReservationAndOwnershipIsEnforced() {
        UUID id=order(5); assertThrows(ApiException.class,()->orders.accessible(new Actor(UUID.randomUUID(),"CUSTOMER"),id,false));
        assertThrows(ApiException.class,()->advance(id,OrderState.DELIVERED));
        orders.transition(customer,id,OrderState.CANCELLED,key());
        var stock=db.one("SELECT quantity,reserved FROM inventory WHERE warehouse_id=? AND product_id=?",warehouse,product);
        assertEquals(5,Store.integer(stock,"quantity")); assertEquals(0,Store.integer(stock,"reserved"));
    }
    @Test void concurrencyNeverAssignsSameDriverTwice() throws Exception {
        // Isolate this scenario from other test drivers.
        db.update("UPDATE driver SET status='OFFLINE' WHERE workload=0"); driver(10);
        UUID first=order(2),second=order(2); packed(first); packed(second); var gate=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(UUID id:List.of(first,second)) jobs.add(pool.submit(()->{gate.await();try { orders.assign(admin,id,key());return true;} catch(ApiException e){assertEquals("NO_DRIVER",e.code);return false;}}));
            gate.countDown();int wins=0;for(var job:jobs)if(job.get(20,TimeUnit.SECONDS))wins++;assertEquals(1,wins);
        }
    }
    @Test void capacityAndWarehouseStatusAreHardConstraints() {
        db.update("UPDATE driver SET status='OFFLINE' WHERE workload=0"); driver(1); UUID id=order(3); packed(id);
        assertThrows(ApiException.class,()->orders.assign(admin,id,key()));
        db.update("UPDATE warehouse SET status='FULL' WHERE id=?",warehouse);
        assertThrows(ApiException.class,()->order(1));
    }
    @Test void refreshRotationAndLogoutRevocation() {
        String email=key()+"@test.invalid";
        var session=auth.register("Test account",email,"long-test-password-only");
        String token=(String)session.get("refreshToken"); var refreshed=auth.refresh(token); assertNotNull(refreshed.get("accessToken"));
        assertThrows(ApiException.class,()->auth.refresh(token));
        UUID id=(UUID)((Map<?,?>)session.get("user")).get("id"); auth.logout(id);
        assertThrows(ApiException.class,()->auth.refresh((String)refreshed.get("refreshToken")));
    }
}
