package com.example.erprukainventario.core.data;

import android.content.Context;
import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.notifications.SyncConflictNotifier;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Cambios de estado con control de concurrencia (guía §11: resolución de conflictos).
 *
 * Con conexión: transacción. Firestore lee el documento "guardia" (el ajuste, el
 * pedido, la transferencia...), se valida su estado y se escriben todos los efectos;
 * si otro usuario lo modificó entretanto, Firestore reintenta con el dato nuevo y la
 * validación lo rechaza. Así dos supervisores no pueden aprobar el mismo ajuste ni
 * dos operarios despachar el mismo pedido (no se duplica el stock movido).
 *
 * Sin conexión: se valida contra el caché local y se encola un batch. La respuesta
 * es inmediata (no se espera al servidor, que no responderá hasta volver la red).
 * El stock usa incrementos, que se suman sin pisarse al sincronizar.
 */
@Singleton
public class GuardedCommit {

    /** Valida el estado vigente; devuelve el mensaje de error o null si se puede aplicar. */
    public interface Check {
        String error(DocumentSnapshot current);
    }

    /** Agrega las escrituras y devuelve el mensaje de éxito. Debe ser repetible (sin efectos externos). */
    public interface Build {
        String build(DocumentSnapshot current, Writes writes);
    }

    private static final String TAG = "GuardedCommit";

    private final FirebaseFirestore firestore;
    private final SyncStatusMonitor sync;
    private final Context appContext;

    @Inject
    public GuardedCommit(FirebaseFirestore firestore, SyncStatusMonitor sync, @ApplicationContext Context appContext) {
        this.firestore = firestore;
        this.sync = sync;
        this.appContext = appContext;
    }

    public void run(DocumentReference guard, Check check, Build build, String failureMessage,
                    RepositoryCallback<OperationResult> callback) {
        if (!sync.isOnline()) {
            runOffline(guard, check, build, failureMessage, callback);
            return;
        }
        firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(guard);
            String error = check.error(current);
            if (error != null) throw new Rejected(error);
            return build.build(current, Writes.of(transaction));
        }).addOnSuccessListener(message -> callback.onResult(OperationResult.ok(message)))
                .addOnFailureListener(e -> {
                    Rejected rejected = findRejected(e);
                    if (rejected != null) {
                        callback.onResult(OperationResult.error(rejected.getMessage()));
                    } else if (isUnavailable(e)) {
                        // La red se cayó a mitad de camino: se guarda en el dispositivo.
                        runOffline(guard, check, build, failureMessage, callback);
                    } else {
                        Log.e(TAG, "Transacción falló", e);
                        callback.onResult(OperationResult.error(failureMessage));
                    }
                });
    }

    private void runOffline(DocumentReference guard, Check check, Build build, String failureMessage,
                            RepositoryCallback<OperationResult> callback) {
        guard.get().addOnSuccessListener(current -> { // sin red, get() responde desde el caché
            String error = check.error(current);
            if (error != null) {
                callback.onResult(OperationResult.error(error));
                return;
            }
            WriteBatch batch = firestore.batch();
            String message = build.build(current, Writes.of(batch));
            commitLocally(batch, message + OFFLINE_SUFFIX, failureMessage, callback);
        }).addOnFailureListener(e -> callback.onResult(OperationResult.error(
                "Sin conexión y sin datos guardados en el dispositivo para validar la operación.")));
    }

    public static final String OFFLINE_SUFFIX =
            "\n\nSin conexión: quedó guardado en el dispositivo y se sincronizará al volver la red.";

    /**
     * Commit de un batch que no espera al servidor si no hay red. Úsese también para
     * escrituras que no necesitan guardia (altas, recepciones con incrementos).
     */
    public void commitLocally(WriteBatch batch, String successMessage, String failureMessage,
                              RepositoryCallback<OperationResult> callback) {
        Task<Void> task = batch.commit();
        if (!sync.isOnline()) {
            sync.refresh();
            boolean tagged = successMessage.isEmpty() || successMessage.endsWith(OFFLINE_SUFFIX);
            callback.onResult(OperationResult.ok(tagged ? successMessage : successMessage + OFFLINE_SUFFIX));
            task.addOnCompleteListener(t -> sync.refresh());
            // Al volver la red, las reglas del servidor pueden rechazar el batch si otro
            // usuario se adelantó. Se descarta completo (sin stock a medias) y se avisa.
            task.addOnFailureListener(e -> {
                Log.e(TAG, "Escritura offline rechazada al sincronizar", e);
                String detail = successMessage.replace(OFFLINE_SUFFIX, "").split("\n")[0].trim();
                SyncConflictNotifier.notifyRejected(appContext, detail.isEmpty() ? "operación sin conexión" : detail);
            });
            return;
        }
        task.addOnSuccessListener(v -> callback.onResult(OperationResult.ok(successMessage)))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Commit falló", e);
                    callback.onResult(OperationResult.error(failureMessage));
                });
    }

    private static Rejected findRejected(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof Rejected) return (Rejected) t;
        }
        return null;
    }

    private static boolean isUnavailable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) t).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return true;
            }
        }
        return false;
    }

    /** El estado vigente no permite la operación (otro usuario se adelantó). */
    private static class Rejected extends RuntimeException {
        Rejected(String message) {
            super(message);
        }
    }
}
