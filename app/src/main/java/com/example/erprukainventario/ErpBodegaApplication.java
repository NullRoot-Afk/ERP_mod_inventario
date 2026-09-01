package com.example.erprukainventario;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;

/**
 * Punto de entrada de Hilt. Sin esta clase (y sin registrarla en el
 * AndroidManifest con android:name), ninguna @Inject de la app funciona
 * en tiempo de ejecución, aunque el proyecto compile bien.
 */
@HiltAndroidApp
public class ErpBodegaApplication extends Application {
}
