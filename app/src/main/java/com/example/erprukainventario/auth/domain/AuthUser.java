package com.example.erprukainventario.auth.domain;

/** Usuario autenticado: lo que queda en sesión tras un login exitoso (online u offline). */
public class AuthUser {

    private final String id;
    private final String fullName;
    private final UserRole role;
    private final String warehouseId;
    private final String warehouseName;

    public AuthUser(String id, String fullName, UserRole role, String warehouseId, String warehouseName) {
        this.id = id;
        this.fullName = fullName;
        this.role = role;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
    public String getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
}
