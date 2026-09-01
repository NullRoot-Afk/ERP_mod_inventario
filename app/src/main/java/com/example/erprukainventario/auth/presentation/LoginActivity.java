package com.example.erprukainventario.auth.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityLoginBinding;
import com.example.erprukainventario.home.presentation.HomeActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

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
        binding.etUsername.addTextChangedListener(simpleWatcher(viewModel::onUsernameChanged));
        binding.etPassword.addTextChangedListener(simpleWatcher(viewModel::onPasswordChanged));
        binding.btnLogin.setOnClickListener(v -> viewModel.onLoginClicked());
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, this::render);
    }

    private void render(LoginUiState state) {
        binding.progressLogin.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(state.isSubmitEnabled());
        binding.btnLogin.setText(state.isLoading ? "" : getString(R.string.login_button_submit));

        if (state.errorMessage != null) {
            binding.tvLoginError.setText(state.errorMessage);
            binding.tvLoginError.setVisibility(View.VISIBLE);
        } else {
            binding.tvLoginError.setVisibility(View.GONE);
        }

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
        startActivity(new Intent(this, HomeActivity.class));
        finish(); // el usuario no debe poder volver al login con "atrás"
    }

    private TextWatcher simpleWatcher(OnTextChanged onChanged) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                onChanged.onChanged(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private interface OnTextChanged {
        void onChanged(String value);
    }
}