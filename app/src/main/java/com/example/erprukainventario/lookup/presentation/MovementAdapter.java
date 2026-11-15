package com.example.erprukainventario.lookup.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.databinding.ItemMovementBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Historial de movimientos de un producto: quién, cuándo, qué acción (guía §10). */
public class MovementAdapter extends RecyclerView.Adapter<MovementAdapter.ViewHolder> {

    private final SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
    private List<Movement> items = new ArrayList<>();

    public void submit(List<Movement> movements) {
        this.items = movements;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemMovementBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Movement m = items.get(position);
        ItemMovementBinding b = holder.b;
        b.tvType.setText(m.getType().getLabel());
        String when = m.getCreatedAt() != null ? format.format(m.getCreatedAt()) : "pendiente de sincronizar";
        b.tvDetail.setText(m.getReference() + " · " + m.getUserName() + " · " + when);
        b.tvQuantity.setText((m.getQuantity() > 0 ? "+" : "") + m.getQuantity());
        b.tvQuantity.setTextColor(b.getRoot().getContext().getColor(
                m.getQuantity() >= 0 ? R.color.text_success : R.color.text_danger));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemMovementBinding b;

        ViewHolder(ItemMovementBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
