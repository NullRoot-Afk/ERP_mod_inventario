package com.example.erprukainventario.notifications;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.erprukainventario.core.data.WorkContext;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** Arranca la revisión periódica de tareas para el usuario en sesión. */
@Singleton
public class TaskAlertsScheduler {

    private static final String PERIODIC = "task_check_periodic";
    private static final String NOW = "task_check_now";

    private final Context appContext;
    private final WorkContext workContext;

    @Inject
    public TaskAlertsScheduler(@ApplicationContext Context appContext, WorkContext workContext) {
        this.appContext = appContext;
        this.workContext = workContext;
    }

    /** Llamar al entrar al inicio con sesión iniciada. */
    public void start() {
        String companyId = workContext.companyId();
        if (companyId == null) return;

        TaskNotifier.createChannels(appContext);
        new AlertPrefs(appContext).saveContext(companyId, workContext.userId(), workContext.warehouseId(),
                workContext.canApproveAdjustments());

        Constraints online = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager wm = WorkManager.getInstance(appContext);

        // 15 min es el mínimo que permite WorkManager para trabajos periódicos.
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(TaskCheckWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(online)
                        .build());

        // Una revisión inmediata deja sembradas las tareas actuales (no se notifican).
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(TaskCheckWorker.class).setConstraints(online).build());
    }
}
