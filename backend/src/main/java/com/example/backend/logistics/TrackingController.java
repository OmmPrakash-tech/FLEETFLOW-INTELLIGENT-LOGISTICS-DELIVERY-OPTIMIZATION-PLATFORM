package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.Actor;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {
  private final TrackingService tracking;
  private final Store db;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
  private final Semaphore connections = new Semaphore(64);

  public TrackingController(TrackingService tracking, Store db) {
    this.tracking = tracking;
    this.db = db;
  }

  @GetMapping("/{id}")
  public Object snapshot(@PathVariable UUID id) {
    return tracking.snapshot(Actor.current(), id);
  }

  @PostMapping("/drivers/{id}/position")
  public Object position(@PathVariable UUID id, @Valid @RequestBody Requests.Position r) {
    tracking.position(Actor.current(), id, r);
    return Map.of("message", "Position updated");
  }

  @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
  public SseEmitter stream(@PathVariable UUID id) {
    var actor = Actor.current();
    tracking.snapshot(actor, id);
    if (!connections.tryAcquire())
      throw new ApiException(
          503, "TRACKING_CAPACITY", "Too many tracking connections; retry later");
    var initial = db.one("SELECT token_version FROM app_user WHERE id=?", actor.id());
    var emitter = new SseEmitter(60000L);
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var ref = new java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>>();
    Runnable cleanup =
        () -> {
          if (closed.compareAndSet(false, true)) {
            connections.release();
            var future = ref.get();
            if (future != null) future.cancel(false);
          }
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(
        () -> {
          cleanup.run();
          emitter.complete();
        });
    emitter.onError(e -> cleanup.run());
    var last = new java.util.concurrent.atomic.AtomicReference<String>("");
    ref.set(
        scheduler.scheduleWithFixedDelay(
            () -> {
              if (closed.get()) return;
              try {
                var user =
                    db.one("SELECT active,token_version FROM app_user WHERE id=?", actor.id());
                if (!Boolean.TRUE.equals(user.get("active"))
                    || !user.get("token_version").equals(initial.get("token_version"))) {
                  cleanup.run();
                  emitter.complete();
                  return;
                }
                var snapshot = tracking.snapshot(actor, id);
                String signature = snapshot.toString();
                if (!signature.equals(last.get())) {
                  emitter.send(SseEmitter.event().name("tracking").data(snapshot));
                  last.set(signature);
                } else emitter.send(SseEmitter.event().comment("heartbeat"));
              } catch (Exception e) {
                cleanup.run();
                emitter.completeWithError(e);
              }
            },
            0,
            3,
            TimeUnit.SECONDS));
    if (closed.get()) ref.get().cancel(false);
    return emitter;
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
