package com.example.weathermoblieapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.weathermoblieapp.model.ForecastResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ViewHolder> {
    private List<ForecastResponse.ForecastItem> forecastItems;

    public ForecastAdapter(List<ForecastResponse.ForecastItem> items) {
        this.forecastItems = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forecast, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ForecastResponse.ForecastItem item = forecastItems.get(position);
        
        // Format date/time
        SimpleDateFormat sdf = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
        holder.tvDay.setText(sdf.format(new Date(item.dt * 1000)));
        
        holder.tvTemp.setText(Math.round(item.main.temp) + "°");
        
        String iconUrl = "https://openweathermap.org/img/wn/" + item.weather.get(0).icon + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.ivIcon);
    }

    @Override
    public int getItemCount() {
        return forecastItems != null ? forecastItems.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvTemp;
        ImageView ivIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
            tvTemp = itemView.findViewById(R.id.tvForecastTemp);
            ivIcon = itemView.findViewById(R.id.ivForecastIcon);
        }
    }
}
