package com.example.backend.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public final class Requests {
    private Requests() {}
    public record Product(@NotBlank @Size(max=60) String sku,@NotBlank @Size(max=160) String name,@NotNull @DecimalMin("0.001") BigDecimal weightKg,@NotNull @DecimalMin("0") BigDecimal price) {}
    public record Warehouse(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=500) String address,@Min(-90) @Max(90) double latitude,@Min(-180) @Max(180) double longitude,@Min(1) int capacity,@NotNull WarehouseStatus status) {}
    public enum WarehouseStatus { ACTIVE,BUSY,FULL,MAINTENANCE,INACTIVE }
    public record Stock(@NotNull UUID warehouseId,@NotNull UUID productId,@Min(1) @Max(1000000) int quantity) {}
    public record Item(@NotNull UUID productId,@Min(1) @Max(100000) int quantity) {}
    public record Order(@NotBlank @Size(max=500) String address,@Min(-90) @Max(90) double latitude,@Min(-180) @Max(180) double longitude,@Min(1) @Max(3) int priority,@Min(1) @Max(168) int slaHours,@NotEmpty @Size(max=100) List<@Valid Item> items) {}
    public enum VehicleType { BIKE,VAN,TRUCK }
    public enum VehicleStatus { AVAILABLE,ASSIGNED,MAINTENANCE,INACTIVE }
    public enum DriverStatus { AVAILABLE,ASSIGNED,ON_DELIVERY,OFFLINE,ON_LEAVE }
    public record Vehicle(@NotBlank @Size(max=40) String registrationNumber,@NotNull VehicleType type,@NotNull @DecimalMin("0.001") BigDecimal capacityKg) {}
    public record Driver(@NotBlank @Size(max=120) String name,UUID userId,@NotNull UUID vehicleId,@Min(-90) @Max(90) double latitude,@Min(-180) @Max(180) double longitude) {}
    public record Position(@Min(-90) @Max(90) double latitude,@Min(-180) @Max(180) double longitude) {}
    public record Transition(@NotNull OrderState status) {}
    public record Account(@NotNull Role role,boolean active,boolean forceReset) {}
    public enum Role { ADMIN,OPERATOR,DRIVER,CUSTOMER }
    public record Address(@NotBlank @Size(max=80) String label,@NotBlank @Size(max=500) String address,@Min(-90) @Max(90) double latitude,@Min(-180) @Max(180) double longitude) {}
}
