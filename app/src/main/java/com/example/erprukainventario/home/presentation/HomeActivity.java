package com.example.erprukainventario.home.presentation;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityHomeBinding;
import com.example.erprukainventario.databinding.ItemHomeMenuButtonBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private HomeViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupMenuButtons();
        observeUiState();
    }

    /**
     * Cada botón del menú es el mismo layout (item_home_menu_button.xml)
     * incluido 6 veces. ViewBinding genera un sub-binding por cada
     * <include>, accesible como binding.btnRecepcion, binding.btnPicking,
     * etc. — así evitamos colisión de IDs entre las 6 copias, que sí
     * pasaría con un findViewById() plano sobre toda la Activity.
     */
    private void setupMenuButtons() {
        configureMenuButton(binding.btnRecepcion, R.drawable.ic_cart_24, getString(R.string.home_menu_recepcion),
                () -> Toast.makeText(this, "Recepción — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        configureMenuButton(binding.btnPicking, R.drawable.ic_box_24, getString(R.string.home_menu_picking),
                () -> Toast.makeText(this, "Picking — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        configureMenuButton(binding.btnDespacho, R.drawable.ic_truck_delivery_24, getString(R.string.home_menu_despacho),
                () -> Toast.makeText(this, "Despacho — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        configureMenuButton(binding.btnConteo, R.drawable.ic_list_check_24, getString(R.string.home_menu_conteo),
                () -> Toast.makeText(this, "Conteo — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        configureMenuButton(binding.btnTransferencias, R.drawable.ic_arrows_exchange_24, getString(R.string.home_menu_transferencias),
                () -> Toast.makeText(this, "Transferencias — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        configureMenuButton(binding.btnAjustes, R.drawable.ic_adjustments_24, getString(R.string.home_menu_ajustes),
                () -> Toast.makeText(this, "Ajustes — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());

        binding.btnScanNow.setOnClickListener(v ->
                Toast.makeText(this, "Escaneo rápido — pantalla pendiente de construir", Toast.LENGTH_SHORT).show());
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
            binding.tvSyncStatus.setText(
                    getString(R.string.home_sync_status_format, state.pendingTasksCount)
            );
        });
    }
}