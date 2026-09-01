package com.example.erprukainventario;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Placeholder temporal. Esta Activity se reemplazará por la pantalla
 * "Home" real (menú principal con Recepción, Picking, Despacho, Conteo,
 * Transferencias, Ajustes) cuando lleguemos a construirla.
 *
 * Se simplificó a propósito, quitando el código de "edge-to-edge insets"
 * que traía la plantilla original de Android Studio, porque dependía de
 * un id específico (R.id.main) en activity_main.xml que no necesitamos
 * mientras esta pantalla sea solo un placeholder.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}