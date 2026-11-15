package com.example.erprukainventario.location.presentation;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityLocationStockBinding;

import dagger.hilt.android.AndroidEntryPoint;

/** Stock por ubicación de la bodega del usuario, con acceso a la reubicación. */
@AndroidEntryPoint
public class LocationStockActivity extends AppCompatActivity {

    private ActivityLocationStockBinding binding;
    private LocationStockViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationStockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationStockViewModel.class);

        LocationStockAdapter adapter = new LocationStockAdapter(row -> new AlertDialog.Builder(this)
                .setTitle(row.product.getName())
                .setMessage(getString(R.string.location_product_detail_format, row.product.getLocation(),
                        row.stock, row.product.getMinStock()))
                .setPositiveButton(R.string.location_relocate, (d, w) ->
                        startActivity(RelocationActivity.intent(this, row.product.getCode())))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        binding.rvLocations.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLocations.setAdapter(adapter);

        binding.tvSubtitle.setText(viewModel.warehouseName());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRelocate.setOnClickListener(v -> startActivity(RelocationActivity.intent(this, null)));
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                viewModel.setFilter(s.toString());
            }
        });

        viewModel.isLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));
        viewModel.getSummary().observe(this, binding.tvSummary::setText);
        viewModel.getRows().observe(this, rows -> {
            adapter.submit(rows);
            binding.tvEmpty.setVisibility(rows.isEmpty() && !Boolean.TRUE.equals(viewModel.isLoading().getValue())
                    ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.load(); // refleja reubicaciones recién hechas
    }
}
