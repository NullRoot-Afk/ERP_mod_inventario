package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sesión en memoria (no persistida en disco): vive mientras el proceso
 * de la app esté vivo. Si la app se mata por el sistema, se pierde y
 * habría que re-loguear — eso es aceptable para este MVP; si más adelante
 * queremos "recordar sesión" entre reinicios del proceso, esto se movería
 * a SharedPreferences/DataStore, igual que TenantConfigLocalDataSource.
 */
@Singleton
public class SessionManager {

    private AuthUser currentUser;

    @Inject
    public SessionManager() {
    }

    public void setCurrentUser(AuthUser user) {
        this.currentUser = user;
    }

    public AuthUser getCurrentUser() {
        return currentUser;
    }

    public void clear() {
        currentUser = null;
    }
}