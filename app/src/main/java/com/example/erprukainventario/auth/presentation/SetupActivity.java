package com.example.erprukainventario.auth.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.databinding.ActivitySetupBinding;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SetupActivity extends AppCompatActivity {

    // Se inyecta directo en la Activity (no vía ViewModel) porque esta
    // lectura debe pasar ANTES de que se infle cualquier UI — es una
    // decisión de navegación, no un dato que la pantalla deba mostrar.
    @Inject
    TenantConfigLocalDataSource tenantConfig;

    private ActivitySetupBinding binding;
    private SetupViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lectura síncrona y rápida (SharedPreferences) — si ya está
        // configurado, ni siquiera vale la pena inflar el layout de setup.
        if (tenantConfig.isConfigured()) {
            goToLogin();
            return;
        }

        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SetupViewModel.class);

        setupListeners();
        observeUiState();
    }

    private void setupListeners() {
        binding.etCompanyCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onCompanyCodeChanged(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.btnConfirmSetup.setOnClickListener(v -> viewModel.onConfirmClicked());
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, this::render);
    }

    private void render(SetupUiState state) {
        binding.progressSetup.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        binding.btnConfirmSetup.setEnabled(state.isSubmitEnabled());

        if (state.errorMessage != null) {
            binding.tvSetupError.setText(state.errorMessage);
            binding.tvSetupError.setVisibility(View.VISIBLE);
        } else {
            binding.tvSetupError.setVisibility(View.GONE);
        }

        if (state.configured) {
            goToLogin();
        }
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish(); // el usuario nunca debe poder "volver atrás" a este setup
    }
}