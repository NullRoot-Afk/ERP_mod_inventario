package com.example.erprukainventario.core.scan;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.databinding.ActivityScanSessionBinding;

import java.util.HashMap;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Pantalla de escaneo única para picking, despacho, conteo y transferencias.
 * Se abre con {@link #intent(Context, String, String)}; el "mode" decide qué
 * ScanTaskSource carga y guarda los datos.
 */
@AndroidEntryPoint
public class ScanSessionActivity extends AppCompatActivity {

    private static final String EXTRA_MODE = "extra_mode";
    private static final String EXTRA_TASK_ID = "extra_task_id";

    private ActivityScanSessionBinding binding;
    private ScanSessionViewModel viewModel;
    private ScanLineAdapter adapter;
    private BarcodeScannerController scanner;
    private ScanFeedback feedback;

    public static Intent intent(Context context, String mode, String taskId) {
        return new Intent(context, ScanSessionActivity.class)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_TASK_ID, taskId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ScanSessionViewModel.class);
        feedback = new ScanFeedback(this);
        scanner = new BarcodeScannerController(this, binding.cameraPreviewView, this::onCodeRead);

        adapter = new ScanLineAdapter(this::showEditDialog);
        binding.rvLines.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLines.setAdapter(adapter);

        setupListeners();
        observe();

        viewModel.init(getIntent().getStringExtra(EXTRA_MODE), getIntent().getStringExtra(EXTRA_TASK_ID));
        scanner.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        feedback.release();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        binding.tabPending.setOnClickListener(v -> viewModel.setTab(ScanSessionUiState.FilterTab.PENDING));
        binding.tabDone.setOnClickListener(v -> viewModel.setTab(ScanSessionUiState.FilterTab.DONE));
        binding.tabAll.setOnClickListener(v -> viewModel.setTab(ScanSessionUiState.FilterTab.ALL));

        binding.btnManualEntry.setOnClickListener(v -> {
            scanner.pause();
            Dialogs.textInput(this, getString(R.string.scan_manual_entry), getString(R.string.reception_manual_hint),
                    getString(R.string.reception_manual_search), this::onCodeRead, scanner::resume);
        });

        binding.btnFinish.setOnClickListener(v -> onFinishClicked());
    }

    private void observe() {
        viewModel.getUiState().observe(this, state -> {
            boolean busy = state.isLoading || state.isSaving;
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnFinish.setEnabled(!busy && state.task != null);

            styleTab(binding.tabPending, state.tab == ScanSessionUiState.FilterTab.PENDING);
            styleTab(binding.tabDone, state.tab == ScanSessionUiState.FilterTab.DONE);
            styleTab(binding.tabAll, state.tab == ScanSessionUiState.FilterTab.ALL);

            ScanTask task = state.task;
            if (task == null) return;

            binding.tvScreenLabel.setText(task.getConfig().screenLabel);
            binding.tvTaskCode.setText(task.getCode());
            binding.tvTaskSubtitle.setText(task.getSubtitle());
            binding.btnFinish.setText(task.getConfig().finishLabel);

            int[] progress = viewModel.getProgress();
            binding.tvProgress.setText(getString(task.getConfig().showExpected
                    ? R.string.scan_progress_format : R.string.scan_progress_blind_format, progress[0], progress[1]));

            List<ScanLine> visible = viewModel.getVisibleLines();
            adapter.submit(visible, task.getConfig().showExpected);
            binding.tvEmpty.setVisibility(visible.isEmpty() && !state.isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getMessages().observe(this, event -> {
            String msg = event.consume();
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        viewModel.getFinishResult().observe(this, event -> {
            OperationResult result = event.consume();
            if (result == null) return;
            scanner.pause();
            new AlertDialog.Builder(this)
                    .setTitle(result.isSuccess() ? "Operación registrada" : "No disponible")
                    .setMessage(result.getMessage())
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        setResult(RESULT_OK);
                        finish();
                    })
                    .show();
        });
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        tab.setTextColor(getColor(selected ? R.color.on_accent : R.color.text_primary));
    }

    // ------------------------------------------------------------------
    // Escaneo
    // ------------------------------------------------------------------

    private void onCodeRead(String code) {
        ScanTask task = viewModel.getTask();
        if (task == null) {
            scanner.resume();
            return;
        }
        ScanLine line = viewModel.findLine(code);
        if (line == null) {
            feedback.error();
            Toast.makeText(this, getString(R.string.scan_not_in_task_format, code), Toast.LENGTH_SHORT).show();
            binding.cameraPreviewView.postDelayed(scanner::resume, 1200);
            return;
        }
        feedback.success();
        String meta = task.getConfig().showExpected
                ? getString(R.string.scan_meta_format, line.getScannedQuantity(), line.getExpectedQuantity(), line.getLocation())
                : getString(R.string.scan_meta_blind_format, line.getScannedQuantity(), line.getLocation());

        Dialogs.quantity(this, getString(R.string.reception_product_detected), line.getProductName(), meta,
                task.getConfig().quantityLabel, getString(R.string.reception_add_button), 1, 1,
                qty -> applyQuantity(line, line.getScannedQuantity() + qty),
                scanner::resume);
    }

    /** Tocar una línea permite corregir la cantidad (incluso dejarla en 0). */
    private void showEditDialog(ScanLine line) {
        ScanTask task = viewModel.getTask();
        if (task == null) return;
        scanner.pause();
        String meta = task.getConfig().showExpected
                ? getString(R.string.scan_meta_format, line.getScannedQuantity(), line.getExpectedQuantity(), line.getLocation())
                : getString(R.string.scan_meta_blind_format, line.getScannedQuantity(), line.getLocation());
        Dialogs.quantity(this, getString(R.string.scan_edit_title), line.getProductName(), meta,
                task.getConfig().quantityLabel, getString(R.string.scan_edit_confirm), line.getScannedQuantity(), 0,
                qty -> applyQuantity(line, qty),
                scanner::resume);
    }

    private void applyQuantity(ScanLine line, int newQuantity) {
        String error = viewModel.setQuantity(line.getId(), newQuantity);
        if (error != null) {
            feedback.error();
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------
    // Finalizar / salir
    // ------------------------------------------------------------------

    private void onFinishClicked() {
        ScanTask task = viewModel.getTask();
        if (task == null) return;
        switch (viewModel.checkFinish()) {
            case BLOCKED:
                feedback.error();
                new AlertDialog.Builder(this)
                        .setTitle("Faltan líneas por verificar")
                        .setMessage("Debes escanear todas las líneas antes de finalizar.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;
            case NEEDS_CONFIRMATION:
                new AlertDialog.Builder(this)
                        .setTitle("Líneas incompletas")
                        .setMessage(task.getConfig().incompleteWarning)
                        .setPositiveButton("Continuar", (d, w) -> askPromptsAndFinish(task))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case OK:
            default:
                askPromptsAndFinish(task);
                break;
        }
    }

    private void askPromptsAndFinish(ScanTask task) {
        if (task.getConfig().finishPrompts.length == 0) {
            viewModel.finish(new HashMap<>());
        } else {
            Dialogs.form(this, task.getConfig().finishLabel, task.getConfig().finishPrompts,
                    getString(R.string.scan_confirm), viewModel::finish);
        }
    }

    private void confirmExit() {
        if (!viewModel.hasUnsavedChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("¿Salir sin guardar?")
                .setMessage("Se perderá lo escaneado en esta sesión.")
                .setPositiveButton("Salir", (d, w) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
