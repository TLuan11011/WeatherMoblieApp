package com.example.weathermoblieapp;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.weathermoblieapp.api.WeatherService;
import com.example.weathermoblieapp.model.WeatherResponse;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherWorker extends Worker {

    private static final String API_KEY = "076a2fc17d5de3b400bb4eb9c216d6c1";
    private static final String PREFS   = "weather_prefs";

    public WeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        float lat        = prefs.getFloat("lat", Float.MIN_VALUE);
        float lon        = prefs.getFloat("lon", Float.MIN_VALUE);
        int   threshHigh = prefs.getInt("threshold_high", 35);
        int   threshLow  = prefs.getInt("threshold_low", 10);

        // Chưa có vị trí → bỏ qua
        if (lat == Float.MIN_VALUE || lon == Float.MIN_VALUE) return Result.success();

        try {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.openweathermap.org/data/2.5/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            WeatherService service = retrofit.create(WeatherService.class);

            // Gọi API đồng bộ (bắt buộc trong Worker)
            Call<WeatherResponse> call = service.getCurrentWeather(
                    lat, lon, API_KEY, "metric", "vi");
            Response<WeatherResponse> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                WeatherResponse weather    = response.body();
                int            currentTemp = Math.round(weather.main.temp);
                String         city        = weather.name;
                String         description = weather.weather.get(0).description;
                int            weatherId   = weather.weather.get(0).id;

                // ── Cảnh báo nhiệt độ cao ──
                if (currentTemp >= threshHigh) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            getApplicationContext().getString(R.string.notif_title_heat),
                            getApplicationContext().getString(
                                    R.string.notif_msg_heat, city, currentTemp, threshHigh),
                            3001);
                }

                // ── Cảnh báo nhiệt độ thấp ──
                if (currentTemp <= threshLow) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            getApplicationContext().getString(R.string.notif_title_cold),
                            getApplicationContext().getString(
                                    R.string.notif_msg_cold, city, currentTemp, threshLow),
                            3002);
                }

                // ── Giông bão (200–232) ──
                if (weatherId >= 200 && weatherId <= 232) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            "⛈️ Cảnh Báo Giông Bão",
                            city + " đang có giông bão: " + description,
                            3003);
                }
                // ── Mưa rất to / mưa cực to ──
                else if (weatherId == 502 || weatherId == 503
                        || weatherId == 504 || weatherId == 522) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            "🌧️ Cảnh Báo Mưa Lớn",
                            city + " đang có mưa lớn: " + description,
                            3004);
                }
                // ── Mưa phùn / mưa nhẹ / mưa vừa (300–531) ──
                else if (weatherId >= 300 && weatherId <= 531) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            "🌦️ Thông Báo Mưa",
                            city + " đang có mưa: " + description,
                            3005);
                }
                // ── Tuyết (600–622) ──
                else if (weatherId >= 600 && weatherId <= 622) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            "❄️ Cảnh Báo Tuyết",
                            city + " đang có tuyết: " + description,
                            3006);
                }
                // ── Sương mù / tầm nhìn kém (700–781) ──
                else if (weatherId >= 700 && weatherId <= 781) {
                    WeatherNotificationHelper.show(
                            getApplicationContext(),
                            "🌫️ Cảnh Báo Tầm Nhìn Kém",
                            city + " đang có: " + description,
                            3007);
                }
            }

        } catch (Exception e) {
            Log.e("WeatherWorker", "Lỗi khi kiểm tra thời tiết", e);
            return Result.retry(); // Tự thử lại nếu lỗi mạng
        }

        return Result.success();
    }
}