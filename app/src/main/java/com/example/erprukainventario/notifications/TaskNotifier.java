package com.example.erprukainventario.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.erprukainventario.R;

import java.util.List;

/** Canales y publicación de las notificaciones de tareas y alertas (guía §13). */
final class TaskNotifier {

    static final String CHANNEL_TASKS = "tasks";
    static final String CHANNEL_ALERTS = "alerts";
    private static final int ID_TASKS = 1001;
    private static final int ID_ALERTS = 1002;

    private TaskNotifier() {
    }

    static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_TASKS,
                context.getString(R.string.notif_channel_tasks), NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** Nuevas tareas: una sola notificación que resume todas las nuevas. */
    static void notifyNewTasks(Context context, List<String> titles) {
        if (titles.isEmpty()) return;
        NotificationCompat.Builder builder = base(context, CHANNEL_TASKS)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        if (titles.size() == 1) {
            builder.setContentTitle(context.getString(R.string.notif_new_task))
                    .setContentText(titles.get(0));
        } else {
            NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
            for (String t : titles) inbox.addLine(t);
            String title = context.getString(R.string.notif_new_tasks_format, titles.size());
            builder.setContentTitle(title)
                    .setContentText(titles.get(0))
                    .setStyle(inbox.setBigContentTitle(title));
        }
        post(context, ID_TASKS, builder);
    }

    static void notifyDailyAlerts(Context context, int lowStock, int expiringLots) {
        if (lowStock <= 0 && expiringLots <= 0) return;
        String text = context.getString(R.string.notif_daily_format, lowStock, expiringLots);
        post(context, ID_ALERTS, base(context, CHANNEL_ALERTS)
                .setContentTitle(context.getString(R.string.notif_daily_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text)));
    }

    /** Un cambio hecho sin conexión que el servidor rechazó al sincronizar (conflicto). */
    static void notifyRejectedSync(Context context, String detail) {
        String text = context.getString(R.string.notif_sync_rejected_format, detail);
        post(context, (int) (System.currentTimeMillis() & 0xFFFFFF), base(context, CHANNEL_ALERTS)
                .setContentTitle(context.getString(R.string.notif_sync_rejected_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text)));
    }

    private static NotificationCompat.Builder base(Context context, String channel) {
        // Se abre la pantalla de inicio de la app: la sesión puede haberse perdido
        // (vive en memoria) y el flujo normal pide credenciales si hace falta.
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) launch = new Intent();
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_bell_24)
                .setContentIntent(pending)
                .setAutoCancel(true);
    }

    private static void post(Context context, int id, NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // el usuario no dio permiso: el contador del inicio sigue informando
        }
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }
}
