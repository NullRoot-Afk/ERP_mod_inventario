package com.example.erprukainventario.auth.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.erprukainventario.auth.domain.Company;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Guarda qué empresa quedó configurada en ESTE dispositivo, una sola vez,
 * durante la configuración inicial (no en cada login).
 *
 * Se usa SharedPreferences en vez de Room a propósito: es un solo par
 * clave-valor por dispositivo, no una tabla de registros — Room sería
 * sobre-ingeniería para este caso. Los métodos son síncronos porque
 * SharedPreferences ya es rápido (lee de un archivo cacheado en memoria
 * tras el primer acceso), no hace falta moverlo a un hilo de background
 * como sí hacemos con Room.
 */
@Singleton
public class TenantConfigLocalDataSource {

    private static final String PREFS_NAME = "tenant_config";
    private static final String KEY_COMPANY_ID = "company_id";
    private static final String KEY_COMPANY_NAME = "company_name";

    private final SharedPreferences prefs;

    @Inject
    public TenantConfigLocalDataSource(@ApplicationContext Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isConfigured() {
        return prefs.contains(KEY_COMPANY_ID);
    }

    public Company getConfiguredCompany() {
        String id = prefs.getString(KEY_COMPANY_ID, null);
        String name = prefs.getString(KEY_COMPANY_NAME, null);
        if (id == null) return null;
        return new Company(id, name);
    }

    public void saveCompany(Company company) {
        prefs.edit()
                .putString(KEY_COMPANY_ID, company.getId())
                .putString(KEY_COMPANY_NAME, company.getName())
                .apply();
    }

    /** Solo para soporte técnico: permite reconfigurar el dispositivo a otra empresa. */
    public void clear() {
        prefs.edit().clear().apply();
    }
}
