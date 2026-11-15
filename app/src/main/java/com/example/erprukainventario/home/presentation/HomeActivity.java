package com.example.erprukainventario.home.presentation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.dashboard.presentation.DashboardActivity;
import com.example.erprukainventario.location.presentation.LocationStockActivity;
import com.example.erprukainventario.notifications.TaskAlertsScheduler;

import javax.inject.Inject;

import com.example.erprukainventario.R;
import com.example.erprukainventario.adjustment.presentation.AdjustmentListActivity;
import com.example.erprukainventario.count.presentation.CountListActivity;
import com.example.erprukainventario.databinding.ActivityHomeBinding;
import com.example.erprukainventario.databinding.ItemHomeMenuButtonBinding;
import com.example.erprukainventario.dispatch.presentation.DispatchListActivity;
import com.example.erprukainventario.lookup.presentation.ProductLookupActivity;
import com.example.erprukainventario.picking.presentation.PickingListActivity;
import com.example.erprukainventario.reception.presentation.ReceptionListActivity;
import com.example.erprukainventario.transfer.presentation.TransferListActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private HomeViewModel viewModel;

    @Inject
    TaskAlertsScheduler taskAlerts;

    // Si lo rechaza, la app funciona igual: el contador de tareas del inicio sigue visible.
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupMenuButtons();
        observeUiState();

        taskAlerts.start();
        if (savedInstanceState == null) requestNotificationPermission();
    }

    /**
     * Cada botón del menú es el mismo layout (item_home_menu_button.xml)
     * incluido 8 veces. ViewBinding genera un sub-binding por cada
     * <include>, accesible como binding.btnRecepcion, binding.btnPicking,
     * etc. — así evitamos colisión de IDs entre las 8 copias, que sí
     * pasaría con un findViewById() plano sobre toda la Activity.
     */
    private void setupMenuButtons() {
        configureMenuButton(binding.btnRecepcion, R.drawable.ic_cart_24, getString(R.string.home_menu_recepcion),
                () -> startActivity(new Intent(this, ReceptionListActivity.class)));

        configureMenuButton(binding.btnPicking, R.drawable.ic_box_24, getString(R.string.home_menu_picking),
                () -> startActivity(new Intent(this, PickingListActivity.class)));

        configureMenuButton(binding.btnDespacho, R.drawable.ic_truck_delivery_24, getString(R.string.home_menu_despacho),
                () -> startActivity(new Intent(this, DispatchListActivity.class)));

        configureMenuButton(binding.btnConteo, R.drawable.ic_list_check_24, getString(R.string.home_menu_conteo),
                () -> startActivity(new Intent(this, CountListActivity.class)));

        configureMenuButton(binding.btnTransferencias, R.drawable.ic_arrows_exchange_24, getString(R.string.home_menu_transferencias),
                () -> startActivity(new Intent(this, TransferListActivity.class)));

        configureMenuButton(binding.btnAjustes, R.drawable.ic_adjustments_24, getString(R.string.home_menu_ajustes),
                () -> startActivity(new Intent(this, AdjustmentListActivity.class)));

        configureMenuButton(binding.btnUbicaciones, R.drawable.ic_warehouse_24, getString(R.string.home_menu_ubicaciones),
                () -> startActivity(new Intent(this, LocationStockActivity.class)));

        configureMenuButton(binding.btnDashboard, R.drawable.ic_chart_24, getString(R.string.home_menu_dashboard),
                () -> startActivity(new Intent(this, DashboardActivity.class)));

        // La campana abre las alertas (stock crítico, vencimientos, discrepancias).
        binding.ivBell.setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)));

        binding.btnScanNow.setOnClickListener(v ->
                startActivity(new Intent(this, ProductLookupActivity.class)));
    }

    /** Android 13+ exige pedir permiso para mostrar notificaciones (guía §13). */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver de cualquier módulo, el contador refleja lo recién hecho.
        viewModel.refreshPendingTasks();
    }

    private void configureMenuButton(ItemHomeMenuButtonBinding buttonBinding, int iconRes, String label, Runnable onClick) {
        buttonBinding.ivIcon.setImageResource(iconRes);
        buttonBinding.tvLabel.setText(label);
        buttonBinding.getRoot().setOnClickListener(v -> onClick.run());
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.tvGreeting.setText(getString(R.string.home_greeting_format, state.greetingName));
            binding.tvRoleWarehouse.setText(state.roleLabel + " · " + state.warehouseName);
            binding.tvAvatar.setText(state.initials);
            binding.tvSyncStatus.setText(state.pendingTasksCount < 0
                    ? getString(R.string.home_sync_status_loading)
                    : getString(R.string.home_sync_status_format, state.pendingTasksCount));
        });
    }
}