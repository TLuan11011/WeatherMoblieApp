package com.example.weathermoblieapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.weathermoblieapp.api.WeatherService;
import com.example.weathermoblieapp.model.WeatherResponse;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherWorker extends Worker {

    private static final String CHANNEL_ID = "weather_alerts";
    private static final String API_KEY    = "076a2fc17d5de3b400bb4eb9c216d6c1";

    public WeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);

        float lat          = prefs.getFloat("lat", Float.MIN_VALUE);
        float lon          = prefs.getFloat("lon", Float.MIN_VALUE);
        int   threshHigh   = prefs.getInt("threshold_high", 35);
        int   threshLow    = prefs.getInt("threshold_low", 10);

        if (lat == Float.MIN_VALUE || lon == Float.MIN_VALUE) return Result.success();

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.openweathermap.org/data/2.5/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            WeatherService service = retrofit.create(WeatherService.class);
            Call<WeatherResponse> call = service.getCurrentWeather(lat, lon, API_KEY, "metric", "vi");
            Response<WeatherResponse> response = call.execute(); // synchronous trong Worker

            if (response.isSuccessful() && response.body() != null) {
                WeatherResponse weather = response.body();
                int currentTemp = Math.round(weather.main.temp);
                String cityName = weather.name;

                if (currentTemp >= threshHigh) {
                    String title = getApplicationContext().getString(R.string.notif_title_heat);
                    String msg   = getApplicationContext().getString(
                            R.string.notif_msg_heat, cityName, currentTemp, threshHigh);
                    showNotification(title, msg, 1001);
                } else if (currentTemp <= threshLow) {
                    String title = getApplicationContext().getString(R.string.notif_title_cold);
                    String msg   = getApplicationContext().getString(
                            R.string.notif_msg_cold, cityName, currentTemp, threshLow);
                    showNotification(title, msg, 1002);
                }
            }
        } catch (Exception e) {
            return Result.retry();
        }

        return Result.success();
    }

    private void showNotification(String title, String message, int notifId) {
        NotificationManager nm = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getApplicationContext().getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        nm.notify(notifId, builder.build());
    }
}