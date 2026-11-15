package com.example.erprukainventario.notifications;

import android.content.Context;

/**
 * Avisa cuando un cambio guardado sin conexión es rechazado por el servidor al
 * sincronizar (firestore.rules detectó que otro usuario ya lo había hecho:
 * ajuste ya aprobado, pedido ya despachado, etc.). Nada quedó a medias: el
 * batch completo se descarta, incluido su movimiento de stock.
 */
public final class SyncConflictNotifier {

    private SyncConflictNotifier() {
    }

    public static void notifyRejected(Context context, String detail) {
        TaskNotifier.createChannels(context);
        TaskNotifier.notifyRejectedSync(context, detail);
    }
}
