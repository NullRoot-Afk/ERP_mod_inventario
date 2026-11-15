package com.example.erprukainventario.core.scan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ItemScanLineBinding;

import java.util.ArrayList;
import java.util.List;

public class ScanLineAdapter extends RecyclerView.Adapter<ScanLineAdapter.ViewHolder> {

    public interface OnLineClickListener {
        void onLineClick(ScanLine line);
    }

    private final OnLineClickListener listener;
    private List<ScanLine> lines = new ArrayList<>();
    private boolean showExpected = true;

    public ScanLineAdapter(OnLineClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ScanLine> newLines, boolean showExpected) {
        this.lines = newLines;
        this.showExpected = showExpected;
        notifyDataSetChanged(); // listas cortas; para listas grandes usar DiffUtil
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemScanLineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(lines.get(position), showExpected, listener);
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemScanLineBinding b;

        ViewHolder(ItemScanLineBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ScanLine line, boolean showExpected, OnLineClickListener listener) {
            Context ctx = b.getRoot().getContext();
            b.tvLocation.setText(line.getLocation());
            b.tvProductName.setText(line.getProductName());
            b.tvProductMeta.setText("SKU: " + line.getProductCode() + " · EAN: " + line.getBarcode());
            b.tvCount.setText(showExpected
                    ? line.getScannedQuantity() + " / " + line.getExpectedQuantity() + " un."
                    : line.getScannedQuantity() + " un.");

            int icon;
            int color;
            if (showExpected ? line.isComplete() : line.isStarted()) {
                icon = R.drawable.ic_check_circle_24;
                color = R.color.text_success;
            } else if (line.isStarted()) {
                icon = R.drawable.ic_clock_24;
                color = R.color.text_warning;
            } else {
                icon = R.drawable.ic_box_24;
                color = R.color.text_muted;
            }
            b.imgStatus.setImageResource(icon);
            b.imgStatus.setColorFilter(ctx.getColor(color));
            b.tvCount.setTextColor(ctx.getColor(color == R.color.text_muted ? R.color.text_secondary : color));

            b.getRoot().setOnClickListener(v -> listener.onLineClick(line));
        }
    }
}
