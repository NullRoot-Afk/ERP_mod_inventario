package com.example.erprukainventario.core.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ItemTaskSummaryBinding;

import java.util.ArrayList;
import java.util.List;

public class TaskSummaryAdapter extends RecyclerView.Adapter<TaskSummaryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TaskSummary item);
    }

    private final OnItemClickListener listener;
    private List<TaskSummary> items = new ArrayList<>();

    public TaskSummaryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<TaskSummary> newItems) {
        this.items = newItems;
        notifyDataSetChanged(); // listas cortas; para listas grandes usar DiffUtil
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemTaskSummaryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemTaskSummaryBinding b;

        ViewHolder(ItemTaskSummaryBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(TaskSummary item, OnItemClickListener listener) {
            Context ctx = b.getRoot().getContext();
            b.tvCode.setText(item.code);
            b.tvSubtitle.setText(item.subtitle);
            b.tvMeta.setText(item.meta);
            b.tvMeta.setVisibility(item.meta == null || item.meta.isEmpty() ? View.GONE : View.VISIBLE);
            b.tvStatusBadge.setText(item.badgeLabel);

            int badgeBg;
            int textColor;
            int cardBg;
            switch (item.badge) {
                case WARNING:
                    badgeBg = R.drawable.bg_badge_warning;
                    textColor = R.color.text_warning;
                    cardBg = R.drawable.bg_card_warning;
                    break;
                case SUCCESS:
                    badgeBg = R.drawable.bg_badge_success;
                    textColor = R.color.text_success;
                    cardBg = R.drawable.bg_card_success;
                    break;
                case DANGER:
                    badgeBg = R.drawable.bg_badge_danger;
                    textColor = R.color.text_danger;
                    cardBg = R.drawable.bg_carg_danger;
                    break;
                case NEUTRAL:
                default:
                    badgeBg = R.drawable.bg_badge_neutral;
                    textColor = R.color.text_secondary;
                    cardBg = R.drawable.bg_card_default;
                    break;
            }
            b.tvStatusBadge.setBackgroundResource(badgeBg);
            b.tvStatusBadge.setTextColor(ctx.getColor(textColor));
            b.getRoot().setBackgroundResource(cardBg);
            b.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
