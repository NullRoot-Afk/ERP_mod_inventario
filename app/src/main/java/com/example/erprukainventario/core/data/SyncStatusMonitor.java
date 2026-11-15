package com.example.erprukainventario.core.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Estado de sincronización (guía §11): si hay red y si quedan escrituras locales
 * sin confirmar por el servidor. Firestore guarda esas escrituras en su cola
 * offline y las envía solo al recuperar la conexión.
 */
@Singleton
public class SyncStatusMonitor {

    public enum State {
        /** Con red y sin cambios pendientes. */
        SYNCED,
        /** Con red, enviando cambios hechos sin conexión. */
        SYNCING,
        /** Sin red: lo que se registre queda guardado en el dispositivo. */
        OFFLINE,
        /** Sin red y con cambios esperando para subir. */
        OFFLINE_PENDING
    }

    private static final long PENDING_CHECK_MS = 400;

    private final ConnectivityManager connectivity;
    private final FirebaseFirestore firestore;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(State.SYNCED);
    private volatile boolean online;
    private int checkId;

    @Inject
    public SyncStatusMonitor(@ApplicationContext Context context, FirebaseFirestore firestore) {
        this.connectivity = context.getSystemService(ConnectivityManager.class);
        this.firestore = firestore;
        this.online = computeOnline();
        connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                online = true;
                main.post(SyncStatusMonitor.this::refresh);
            }

            @Override
            public void onLost(@NonNull Network network) {
                online = computeOnline();
                main.post(SyncStatusMonitor.this::refresh);
            }
        });
        refresh();
    }

    public LiveData<State> getState() {
        return state;
    }

    public boolean isOnline() {
        return online;
    }

    /**
     * Recalcula el estado. waitForPendingWrites() termina al instante si no hay
     * nada pendiente; si tarda, hay cambios en la cola local.
     */
    public void refresh() {
        final int id = ++checkId;
        final boolean[] done = {false};
        firestore.waitForPendingWrites().addOnCompleteListener(t -> {
            done[0] = true;
            if (id != checkId) return;
            state.setValue(online ? State.SYNCED : State.OFFLINE);
        });
        main.postDelayed(() -> {
            if (done[0] || id != checkId) return;
            state.setValue(online ? State.SYNCING : State.OFFLINE_PENDING);
        }, PENDING_CHECK_MS);
    }

    private boolean computeOnline() {
        Network network = connectivity.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
