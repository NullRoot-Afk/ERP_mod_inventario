package com.example.erprukainventario.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Revisión periódica (WorkManager, cada ~15 min con red) de tareas asignadas:
 * recepciones pendientes, pedidos por preparar, conteos programados, transferencias
 * por enviar/recibir y (supervisor) ajustes por aprobar. Notifica solo las nuevas
 * respecto de la revisión anterior (guía §13).
 *
 * Una vez al día avisa además del stock crítico y los lotes por vencer (guía §10 y §12).
 *
 * Nota: sin servidor propio (Cloud Functions) no hay push FCM real; este worker
 * cumple el rol con la misma fuente de datos y funciona aunque la app esté cerrada.
 */
public class TaskCheckWorker extends Worker {

    private static final String TAG = "TaskCheckWorker";
    private static final long TIMEOUT_S = 30;
    private static final int LOT_ALERT_DAYS = com.example.erprukainventario.core.domain.Lot.ALERT_DAYS;

    public TaskCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AlertPrefs prefs = new AlertPrefs(getApplicationContext());
        String companyId = prefs.companyId();
        if (companyId == null) return Result.success(); // nadie ha iniciado sesión aún

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        try {
            Map<String, String> tasks = collectTasks(db, prefs, companyId);
            Set<String> seen = prefs.seenTasks();
            if (seen != null) {
                List<String> fresh = new ArrayList<>();
                for (Map.Entry<String, String> e : tasks.entrySet()) {
                    if (!seen.contains(e.getKey())) fresh.add(e.getValue());
                }
                TaskNotifier.notifyNewTasks(getApplicationContext(), fresh);
            }
            prefs.saveSeenTasks(tasks.keySet());

            String today = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
            if (!today.equals(prefs.lastDailyAlert())) {
                dailyAlerts(db, prefs, companyId);
                prefs.saveLastDailyAlert(today);
            }
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Revisión de tareas falló; se reintenta", e);
            return Result.retry();
        }
    }

    /** {clave estable de la tarea: texto para mostrar}. */
    private Map<String, String> collectTasks(FirebaseFirestore db, AlertPrefs prefs, String companyId)
            throws Exception {
        String warehouse = prefs.warehouseId();
        String me = prefs.userId();
        Map<String, String> tasks = new LinkedHashMap<>();

        for (DocumentSnapshot d : get(db.collection("RECEPTIONS").whereEqualTo("companyId", companyId))) {
            String status = status(d);
            if (status.equals("SIN_INICIAR") || status.equals("PARCIAL")) {
                tasks.put("REC:" + d.getId(), "Recepción " + Docs.str(d, "OC", "code") + " · "
                        + Docs.str(d, "", "supplierName"));
            }
        }

        for (DocumentSnapshot d : get(db.collection(FirestoreCollections.PICKING_ORDERS)
                .whereEqualTo("companyId", companyId).whereEqualTo("warehouseId", warehouse))) {
            String status = status(d);
            String assigned = Docs.str(d, "", "assignedToId");
            boolean pickable = status.equals("PENDIENTE") || status.equals("EN_PROCESO") || status.equals("PARCIAL");
            if (pickable && (assigned.isEmpty() || assigned.equals(me))) {
                tasks.put("PK:" + d.getId(), "Pedido " + Docs.str(d, "", "code") + " por preparar · "
                        + Docs.str(d, "", "customerName"));
            }
        }

        for (DocumentSnapshot d : get(db.collection(FirestoreCollections.COUNT_TASKS)
                .whereEqualTo("companyId", companyId).whereEqualTo("warehouseId", warehouse))) {
            if (!status(d).equals("CERRADO")) {
                tasks.put("CT:" + d.getId(), "Conteo programado · " + Docs.str(d, "Conteo", "name"));
            }
        }

        for (DocumentSnapshot d : get(db.collection(FirestoreCollections.TRANSFERS)
                .whereEqualTo("companyId", companyId))) {
            String status = status(d);
            String code = Docs.str(d, "", "code");
            if (status.equals("POR_ENVIAR") && warehouse.equals(Docs.str(d, "", "originWarehouseId"))) {
                tasks.put("TR-S:" + d.getId(), "Transferencia " + code + " por enviar");
            } else if (status.equals("EN_TRANSITO") && warehouse.equals(Docs.str(d, "", "destinationWarehouseId"))) {
                tasks.put("TR-R:" + d.getId(), "Transferencia " + code + " por recibir");
            }
        }

        if (prefs.isSupervisor()) {
            for (DocumentSnapshot d : get(db.collection(FirestoreCollections.ADJUSTMENTS)
                    .whereEqualTo("companyId", companyId).whereEqualTo("warehouseId", warehouse)
                    .whereEqualTo("status", "PENDIENTE"))) {
                tasks.put("AJ:" + d.getId(), "Ajuste por aprobar · " + Docs.str(d, "Producto", "productName"));
            }
        }
        return tasks;
    }

    private void dailyAlerts(FirebaseFirestore db, AlertPrefs prefs, String companyId) throws Exception {
        String warehouse = prefs.warehouseId();

        int low = 0;
        for (DocumentSnapshot d : get(db.collection(FirestoreCollections.PRODUCTS)
                .whereEqualTo("companyId", companyId))) {
            int min = Docs.num(d, "minStock");
            Integer stock = Docs.intMap(d, "stock").get(warehouse);
            if (min > 0 && (stock == null ? 0 : stock) <= min) low++;
        }

        int expiring = 0;
        long limit = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(LOT_ALERT_DAYS);
        for (DocumentSnapshot d : get(db.collection(FirestoreCollections.LOTS)
                .whereEqualTo("companyId", companyId).whereEqualTo("warehouseId", warehouse)
                .whereEqualTo("status", "ACTIVO"))) {
            Date expiry = Docs.date(d, "expiryDate");
            if (expiry != null && expiry.getTime() <= limit && Docs.num(d, "quantity") > 0) expiring++;
        }

        TaskNotifier.notifyDailyAlerts(getApplicationContext(), low, expiring);
    }

    private static List<DocumentSnapshot> get(Query query) throws Exception {
        QuerySnapshot snapshot = Tasks.await(query.get(), TIMEOUT_S, TimeUnit.SECONDS);
        return snapshot.getDocuments();
    }

    private static String status(DocumentSnapshot d) {
        return Docs.str(d, "", "status").trim().toUpperCase(Locale.ROOT);
    }
}
