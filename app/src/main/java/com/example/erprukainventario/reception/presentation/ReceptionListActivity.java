package com.example.erprukainventario.reception.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
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
        adapter = new ReceptionOrderAdapter(order -> {
            Intent intent = new Intent(this, ReceptionDetailActivity.class);
            intent.putExtra(ReceptionDetailActivity.EXTRA_ORDER_ID, order.getId());
            startActivity(intent);
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.onSearchQueryChanged(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.tabPending.setOnClickListener(v -> viewModel.onTabSelected(true));
        binding.tabAll.setOnClickListener(v -> viewModel.onTabSelected(false));

        binding.btnReceiveWithoutPO.setOnClickListener(v ->
                Toast.makeText(this, "Recepción sin OC — función en desarrollo", Toast.LENGTH_SHORT).show()
        );
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(state.isLoading ? View.GONE : View.VISIBLE);
            binding.tvEmpty.setVisibility(!state.isLoading && state.orders.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submitList(state.orders);

            // Actualizar estilo visual de las pestañas
            if (state.showOnlyPending) {
                binding.tabPending.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.tabPending.setTextColor(getColor(R.color.on_accent));
                binding.tabAll.setBackgroundResource(R.drawable.bg_tab_unselected);
                binding.tabAll.setTextColor(getColor(R.color.text_primary));
            } else {
                binding.tabPending.setBackgroundResource(R.drawable.bg_tab_unselected);
                binding.tabPending.setTextColor(getColor(R.color.text_primary));
                binding.tabAll.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.tabAll.setTextColor(getColor(R.color.on_accent));
            }
        });
    }
}
