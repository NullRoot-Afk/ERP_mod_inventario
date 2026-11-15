package com.example.erprukainventario.location.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ItemLocationHeaderBinding;
import com.example.erprukainventario.databinding.ItemLocationProductBinding;

import java.util.ArrayList;
import java.util.List;

public class LocationStockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnProductClick {
        void onClick(LocationStockViewModel.Row row);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PRODUCT = 1;

    private final OnProductClick onClick;
    private List<LocationStockViewModel.Row> rows = new ArrayList<>();

    public LocationStockAdapter(OnProductClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<LocationStockViewModel.Row> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).product == null ? TYPE_HEADER : TYPE_PRODUCT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemLocationHeaderBinding.inflate(inflater, parent, false));
        }
        return new ProductHolder(ItemLocationProductBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        LocationStockViewModel.Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).b.tvHeader.setText(row.header);
            return;
        }
        ItemLocationProductBinding b = ((ProductHolder) holder).b;
        b.tvLocation.setText(row.product.getLocation());
        b.tvName.setText(row.product.getName());
        b.tvCode.setText(row.product.getCode());
        b.tvStock.setText(b.getRoot().getContext().getString(R.string.lookup_units_format, row.stock));
        b.tvStock.setTextColor(b.getRoot().getContext().getColor(
                row.low ? R.color.text_danger : R.color.text_primary));
        b.tvLow.setVisibility(row.low ? View.VISIBLE : View.GONE);
        b.getRoot().setOnClickListener(v -> onClick.onClick(row));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemLocationHeaderBinding b;

        HeaderHolder(ItemLocationHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static class ProductHolder extends RecyclerView.ViewHolder {
        final ItemLocationProductBinding b;

        ProductHolder(ItemLocationProductBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
