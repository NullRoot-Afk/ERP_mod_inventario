package com.example.erprukainventario.reception.presentation;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.databinding.ActivityReceptionListBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReceptionListActivity extends AppCompatActivity {

    private ActivityReceptionListBinding binding;
    private ReceptionListViewModel viewModel;
    private ReceptionOrderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionListViewModel.class);

        setupList();
        setupListeners();
        observeUiState();
    }

    private void setupList() {
        adapter = new ReceptionOrderAdapter(order ->
                // TODO: navegar al detalle/escaneo de esta OC cuando exista esa pantalla.
                Toast.makeText(this, "Abrir " + order.getCode() + " — pantalla pendiente", Toast.LENGTH_SHORT).show()
        );
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnReceiveWithoutPO.setOnClickListener(v ->
                // TODO: navegar a la pantalla de recepción sin OC cuando exista.
                Toast.makeText(this, "Recepción sin OC — pantalla pendiente", Toast.LENGTH_SHORT).show()
        );
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(state.isLoading ? View.GONE : View.VISIBLE);
            binding.tvEmpty.setVisibility(!state.isLoading && state.orders.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submitList(state.orders);
        });
    }
}