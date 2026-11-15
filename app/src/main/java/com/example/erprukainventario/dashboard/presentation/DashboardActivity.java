package com.example.erprukainventario.dashboard.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.adjustment.presentation.AdjustmentListActivity;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.ui.LotViews;
import com.example.erprukainventario.databinding.ActivityDashboardBinding;
import com.example.erprukainventario.databinding.ItemDiscrepancyBinding;
import com.example.erprukainventario.databinding.ItemKpiTileBinding;
import com.example.erprukainventario.databinding.ItemLocationProductBinding;
import com.example.erprukainventario.databinding.ItemProductivityBinding;
import com.example.erprukainventario.location.presentation.LocationStockActivity;
import com.example.erprukainventario.reception.domain.Discrepancy;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Dashboard y alertas (guía §12). Lo abren todos los roles desde la campana del
 * inicio; la productividad por operario y la resolución de discrepancias solo
 * se muestran a supervisor y jefe de bodega.
 */
@AndroidEntryPoint
public class DashboardActivity extends AppCompatActivity {

    private static final int MAX_LOW_STOCK_ROWS = 10;

    private ActivityDashboardBinding binding;
    private DashboardViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        boolean supervisor = viewModel.isSupervisor();

        binding.tvTitle.setText(supervisor ? R.string.dashboard_title : R.string.dashboard_title_operator);
        binding.tvSubtitle.setText(viewModel.warehouseName());
        binding.sectionProductivity.setVisibility(supervisor ? View.VISIBLE : View.GONE);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAdjustments.setOnClickListener(v -> startActivity(new Intent(this, AdjustmentListActivity.class)));

        tile(binding.kpiStock, R.string.dashboard_kpi_stock);
        tile(binding.kpiLowStock, R.string.dashboard_kpi_low_stock);
        tile(binding.kpiDiscrepancies, R.string.dashboard_kpi_discrepancies);
        tile(binding.kpiLots, R.string.dashboard_kpi_lots);
        binding.kpiStock.getRoot().setOnClickListener(v -> startActivity(new Intent(this, LocationStockActivity.class)));
        binding.kpiLowStock.getRoot().setOnClickListener(v -> startActivity(new Intent(this, LocationStockActivity.class)));

        observe(supervisor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void tile(ItemKpiTileBinding tile, int label) {
        tile.tvLabel.setText(label);
    }

    private void observe(boolean supervisor) {
        viewModel.isLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getMessage().observe(this, event -> {
            String text = event.consume();
            if (text != null) Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        });

        viewModel.getKpis().observe(this, k -> {
            binding.kpiStock.tvValue.setText(String.valueOf(k.totalUnits));
            binding.kpiStock.tvSub.setText(getString(R.string.dashboard_kpi_stock_sub, k.skuCount));

            binding.kpiLowStock.tvValue.setText(String.valueOf(k.lowStock));
            binding.kpiLowStock.tvValue.setTextColor(getColor(k.lowStock > 0 ? R.color.text_danger : R.color.text_success));
            binding.kpiLowStock.tvSub.setText(R.string.dashboard_kpi_low_stock_sub);

            int disc = k.pendingAdjustments + k.openDiscrepancies;
            binding.kpiDiscrepancies.tvValue.setText(String.valueOf(disc));
            binding.kpiDiscrepancies.tvValue.setTextColor(getColor(disc > 0 ? R.color.text_warning : R.color.text_success));
            binding.kpiDiscrepancies.tvSub.setText(getString(R.string.dashboard_kpi_discrepancies_sub,
                    k.pendingAdjustments, k.openDiscrepancies));

            binding.kpiLots.tvValue.setText(String.valueOf(k.expiringLots));
            binding.kpiLots.tvValue.setTextColor(getColor(k.expiringLots > 0 ? R.color.text_warning : R.color.text_success));
            binding.kpiLots.tvSub.setText(getString(R.string.dashboard_kpi_lots_sub, com.example.erprukainventario.core.domain.Lot.ALERT_DAYS));

            binding.btnAdjustments.setText(getString(R.string.dashboard_adjustments_format, k.pendingAdjustments));
        });

        viewModel.getLowStock().observe(this, this::renderLowStock);

        viewModel.getExpiringLots().observe(this, list -> {
            LotViews.fill(binding.llLots, list, true, viewModel::retireLot);
            binding.tvNoLots.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getDiscrepancies().observe(this, list -> renderDiscrepancies(list, supervisor));

        viewModel.getProductivity().observe(this, this::renderProductivity);
    }

    private void renderLowStock(List<Product> list) {
        binding.llLowStock.removeAllViews();
        binding.tvNoLowStock.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        String warehouse = viewModel.warehouseId();
        for (int i = 0; i < Math.min(list.size(), MAX_LOW_STOCK_ROWS); i++) {
            Product p = list.get(i);
            ItemLocationProductBinding b = ItemLocationProductBinding.inflate(inflater, binding.llLowStock, false);
            b.tvLocation.setText(p.getLocation());
            b.tvName.setText(p.getName());
            b.tvCode.setText(getString(R.string.dashboard_min_format, p.getCode(), p.getMinStock()));
            b.tvStock.setText(getString(R.string.lookup_units_format, p.stockIn(warehouse)));
            b.tvStock.setTextColor(getColor(R.color.text_danger));
            binding.llLowStock.addView(b.getRoot());
        }
    }

    private void renderDiscrepancies(List<Discrepancy> list, boolean supervisor) {
        binding.llDiscrepancies.removeAllViews();
        binding.tvNoDiscrepancies.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (Discrepancy d : list) {
            ItemDiscrepancyBinding b = ItemDiscrepancyBinding.inflate(getLayoutInflater(), binding.llDiscrepancies, false);
            b.tvTitle.setText(d.getType().getLabel() + " · " + d.getQuantity() + " un. · " + d.getProductName());
            String when = d.getCreatedAt() != null ? format.format(d.getCreatedAt()) : getString(R.string.pending_sync);
            String detail = d.getReceptionCode() + " · " + d.getCreatedByName() + " · " + when
                    + (d.getNotes().isEmpty() ? "" : "\n" + d.getNotes());
            b.tvDetail.setText(detail);
            if (d.hasPhoto()) {
                b.btnPhoto.setVisibility(View.VISIBLE);
                b.btnPhoto.setOnClickListener(v -> showPhoto(d));
            }
            if (supervisor) {
                b.btnResolve.setVisibility(View.VISIBLE);
                b.btnResolve.setOnClickListener(v -> viewModel.resolveDiscrepancy(d));
            }
            binding.llDiscrepancies.addView(b.getRoot());
        }
    }

    private void showPhoto(Discrepancy d) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setMinimumHeight((int) (200 * getResources().getDisplayMetrics().density));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(d.getProductName())
                .setMessage(R.string.discrepancy_loading_photo)
                .setView(image)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        viewModel.loadPhoto(d.getEvidenceId(), bitmap -> {
            if (!dialog.isShowing()) return;
            if (bitmap == null) {
                dialog.setMessage(getString(R.string.discrepancy_photo_error));
            } else {
                dialog.setMessage(null);
                image.setImageBitmap(bitmap);
            }
        });
    }

    private void renderProductivity(List<DashboardViewModel.Productivity> list) {
        binding.llProductivity.removeAllViews();
        binding.tvNoProductivity.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        binding.llProductivity.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        int max = 1;
        for (DashboardViewModel.Productivity p : list) max = Math.max(max, p.operations);
        for (DashboardViewModel.Productivity p : list) {
            ItemProductivityBinding b = ItemProductivityBinding.inflate(getLayoutInflater(), binding.llProductivity, false);
            b.tvName.setText(p.userName);
            b.tvValue.setText(getString(R.string.dashboard_productivity_format, p.operations, p.units));
            b.pbShare.setMax(max);
            b.pbShare.setProgress(p.operations);
            binding.llProductivity.addView(b.getRoot());
        }
    }
}
