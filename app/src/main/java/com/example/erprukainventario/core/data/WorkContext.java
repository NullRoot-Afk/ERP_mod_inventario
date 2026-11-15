package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.SessionManager;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.auth.domain.UserRole;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * "Quién, dónde y para qué empresa" en un solo lugar: empresa configurada,
 * usuario en sesión, su bodega y su rol. Todos los módulos lo usan para
 * filtrar datos y para firmar los movimientos (trazabilidad).
 *
 * Si la sesión se perdió (proceso reiniciado), entrega valores por defecto
 * en vez de null para que ninguna pantalla se caiga.
 */
@Singleton
public class WorkContext {

    public static final String DEFAULT_WAREHOUSE_ID = "default";
    private static final String DEFAULT_WAREHOUSE_NAME = "Bodega central";

    private final TenantConfigLocalDataSource tenantConfig;
    private final SessionManager sessionManager;

    @Inject
    public WorkContext(TenantConfigLocalDataSource tenantConfig, SessionManager sessionManager) {
        this.tenantConfig = tenantConfig;
        this.sessionManager = sessionManager;
    }

    /** null si el dispositivo aún no tiene empresa configurada. */
    public String companyId() {
        Company company = tenantConfig.getConfiguredCompany();
        return company != null ? company.getId() : null;
    }

    public String warehouseId() {
        AuthUser user = sessionManager.getCurrentUser();
        String id = user != null ? user.getWarehouseId() : null;
        return id != null && !id.isEmpty() ? id : DEFAULT_WAREHOUSE_ID;
    }

    public String warehouseName() {
        AuthUser user = sessionManager.getCurrentUser();
        String name = user != null ? user.getWarehouseName() : null;
        return name != null && !name.isEmpty() ? name : DEFAULT_WAREHOUSE_NAME;
    }

    public String userId() {
        AuthUser user = sessionManager.getCurrentUser();
        return user != null && user.getId() != null ? user.getId() : "anonimo";
    }

    public String userName() {
        AuthUser user = sessionManager.getCurrentUser();
        return user != null && user.getFullName() != null ? user.getFullName() : "Usuario";
    }

    public UserRole role() {
        AuthUser user = sessionManager.getCurrentUser();
        return user != null && user.getRole() != null ? user.getRole() : UserRole.OPERARIO;
    }

    // ---- Permisos por rol (guía §1: un operario no aprueba ajustes) ----

    public boolean canApproveAdjustments() {
        return role() != UserRole.OPERARIO;
    }

    public boolean canCreateCounts() {
        return role() != UserRole.OPERARIO;
    }
}
