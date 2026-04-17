package com.example.weathermoblieapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.weathermoblieapp.model.DailyForecastItem;

import java.util.List;

public class DailyForecastAdapter extends RecyclerView.Adapter<DailyForecastAdapter.ViewHolder> {

    private List<DailyForecastItem> items;

    public DailyForecastAdapter(List<DailyForecastItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_forecast, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyForecastItem item = items.get(position);

        holder.tvDayName.setText(item.dayName);
        holder.tvRainProb.setText(item.rainPercent + "%");

        // Hiển thị mm mưa — ẩn nếu = 0
        if (item.rainMm > 0) {
            holder.tvRainMm.setVisibility(View.VISIBLE);
            holder.tvRainMm.setText(String.format("%.1f mm", item.rainMm));
        } else {
            holder.tvRainMm.setVisibility(View.GONE);
        }

        holder.tvTempMax.setText(Math.round(item.tempMax) + "°");
        holder.tvTempMin.setText(Math.round(item.tempMin) + "°");

        String iconUrl = "https://openweathermap.org/img/wn/" + item.icon + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.ivDailyIcon);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView  tvDayName, tvRainProb, tvRainMm, tvTempMax, tvTempMin;
        ImageView ivDailyIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvDayName  = itemView.findViewById(R.id.tvDayName);
            tvRainProb = itemView.findViewById(R.id.tvRainProb);
            tvRainMm   = itemView.findViewById(R.id.tvRainMm);
            ivDailyIcon = itemView.findViewById(R.id.ivDailyIcon);
            tvTempMax  = itemView.findViewById(R.id.tvTempMax);
            tvTempMin  = itemView.findViewById(R.id.tvTempMin);
        }
    }
}