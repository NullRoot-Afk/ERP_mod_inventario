package com.example.erprukainventario.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Lo que el worker de alertas necesita recordar entre ejecuciones. La sesión de
 * la app vive solo en memoria (SessionManager), así que el contexto del usuario
 * (empresa, bodega, rol) se copia aquí al entrar al inicio.
 */
class AlertPrefs {

    private static final String FILE = "task_alerts";
    private static final String KEY_COMPANY = "companyId";
    private static final String KEY_USER = "userId";
    private static final String KEY_WAREHOUSE = "warehouseId";
    private static final String KEY_SUPERVISOR = "supervisor";
    private static final String KEY_SEEN = "seenTasks";
    private static final String KEY_LAST_DAILY = "lastDailyAlert";

    private final SharedPreferences prefs;

    AlertPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    void saveContext(String companyId, String userId, String warehouseId, boolean supervisor) {
        SharedPreferences.Editor e = prefs.edit()
                .putString(KEY_COMPANY, companyId)
                .putString(KEY_USER, userId)
                .putString(KEY_WAREHOUSE, warehouseId)
                .putBoolean(KEY_SUPERVISOR, supervisor);
        // Otro usuario o bodega: sus tareas "vistas" no sirven, se vuelve a sembrar.
        if (!userId.equals(prefs.getString(KEY_USER, null))
                || !warehouseId.equals(prefs.getString(KEY_WAREHOUSE, null))) {
            e.remove(KEY_SEEN).remove(KEY_LAST_DAILY);
        }
        e.apply();
    }

    String companyId() { return prefs.getString(KEY_COMPANY, null); }
    String userId() { return prefs.getString(KEY_USER, ""); }
    String warehouseId() { return prefs.getString(KEY_WAREHOUSE, ""); }
    boolean isSupervisor() { return prefs.getBoolean(KEY_SUPERVISOR, false); }

    /** null si nunca se sembró (primera ejecución: no se notifica lo que ya existía). */
    Set<String> seenTasks() {
        Set<String> seen = prefs.getStringSet(KEY_SEEN, null);
        return seen != null ? new HashSet<>(seen) : null;
    }

    void saveSeenTasks(Set<String> ids) {
        prefs.edit().putStringSet(KEY_SEEN, new HashSet<>(ids)).apply();
    }

    String lastDailyAlert() { return prefs.getString(KEY_LAST_DAILY, ""); }

    void saveLastDailyAlert(String day) {
        prefs.edit().putString(KEY_LAST_DAILY, day).apply();
    }
}
