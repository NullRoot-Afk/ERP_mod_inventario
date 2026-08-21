package com.example.erprukainventario.auth.presentation;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.databinding.ActivityLoginBinding;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    // Se guarda la lista actual para mapear la posición elegida en el
    // AutoCompleteTextView de vuelta a un objeto Warehouse.
    private List<Warehouse> currentWarehouses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupListeners();
        observeUiState();
    }

    private void setupListeners() {
        binding.etUsername.addTextChangedListener(simpleWatcher(text -> viewModel.onUsernameChanged(text)));
        binding.etPassword.addTextChangedListener(simpleWatcher(text -> viewModel.onPasswordChanged(text)));

        binding.actvWarehouse.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentWarehouses.size()) {
                viewModel.onWarehouseSelected(currentWarehouses.get(position));
            }
        });

        binding.btnLogin.setOnClickListener(v -> viewModel.onLoginClicked());
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, this::render);
    }

    private void render(LoginUiState state) {
        // --- Selector de bodega: solo se re-arma el adapter si la lista cambió ---
        if (!state.warehouses.equals(currentWarehouses)) {
            currentWarehouses = state.warehouses;
            List<String> names = new ArrayList<>();
            for (Warehouse w : currentWarehouses) names.add(w.getName());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1, names
            );
            binding.actvWarehouse.setAdapter(adapter);
        }

        // --- Loading ---
        binding.progressLogin.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(state.isSubmitEnabled());
        binding.btnLogin.setText(state.isLoading ? "" : getString(R.string.login_button_submit));

        // --- Error ---
        if (state.errorMessage != null) {
            binding.tvLoginError.setText(state.errorMessage);
            binding.tvLoginError.setVisibility(View.VISIBLE);
        } else {
            binding.tvLoginError.setVisibility(View.GONE);
        }

        // --- Login exitoso ---
        if (state.loggedInUser != null) {
            if (state.loggedInFromCache) {
                Toast.makeText(
                        this,
                        "Sesión iniciada sin conexión. Se sincronizará al recuperar señal.",
                        Toast.LENGTH_LONG
                ).show();
            }
            navigateToHome();
        }
    }

    private void navigateToHome() {
        // TODO: reemplazar por el intent real hacia HomeActivity una vez
        // definida la navegación completa del módulo.
        // startActivity(new Intent(this, HomeActivity.class));
        // finish();
    }

    /** Helper para no repetir el boilerplate de TextWatcher en cada EditText. */
    private TextWatcher simpleWatcher(OnTextChanged onChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onChanged.onChanged(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
    }

    private interface OnTextChanged {
        void onChanged(String value);
    }
}