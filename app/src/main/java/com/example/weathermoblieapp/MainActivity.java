package com.example.weathermoblieapp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.example.weathermoblieapp.api.WeatherService;
import com.example.weathermoblieapp.model.DailyForecastItem;
import com.example.weathermoblieapp.model.ForecastResponse;
import com.example.weathermoblieapp.model.WeatherResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.google.android.material.slider.Slider;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String API_KEY   = "076a2fc17d5de3b400bb4eb9c216d6c1";
    private static final int    LOC_PERM  = 1000;
    private static final int    NOTIF_PERM = 1001;
    private static final String PREFS     = "weather_prefs";
    private static final String WORK_TAG  = "weather_check";

    private FusedLocationProviderClient fusedLocationClient;
    private WeatherService weatherService;
    private GoogleMap mMap;
    private TileOverlay currentTileOverlay;

    // Views - Weather Card
    private TextView  tvCityName, tvTemperature, tvDescription, tvHumidity, tvWindSpeed;
    private ImageView ivWeatherIcon;

    // Views - Forecast
    private RecyclerView         rvForecast, rvDailyForecast;
    private TemperatureChartView temperatureChart;

    // Views - Threshold
    private Slider   sliderHigh, sliderLow;
    private TextView tvHighValue, tvLowValue;

    // Views - Controls
    private Button btnToggleUnit;
    private Button btnLayerTemp, btnLayerClouds, btnLayerRain, btnLayerWind;

    private boolean isCelsius     = true;
    private double  currentLat    = 0;
    private double  currentLon    = 0;
    private int     thresholdHigh = 35;
    private int     thresholdLow  = 10;

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadThresholds();
        setupRetrofit();
        setupChartScrollSync();
        setupSliders();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        checkPermissions();

        btnToggleUnit.setOnClickListener(v -> {
            isCelsius = !isCelsius;
            btnToggleUnit.setText(isCelsius
                    ? getString(R.string.btn_switch_to_f)
                    : getString(R.string.btn_switch_to_c));
            fetchWeatherData(currentLat, currentLon);
        });
    }

    // ─────────────────────────────────────────────
    // Init Views
    // ─────────────────────────────────────────────

    private void initViews() {
        tvCityName       = findViewById(R.id.tvCityName);
        tvTemperature    = findViewById(R.id.tvTemperature);
        tvDescription    = findViewById(R.id.tvDescription);
        tvHumidity       = findViewById(R.id.tvHumidity);
        tvWindSpeed      = findViewById(R.id.tvWindSpeed);
        ivWeatherIcon    = findViewById(R.id.ivWeatherIcon);
        rvForecast       = findViewById(R.id.rvForecast);
        rvDailyForecast  = findViewById(R.id.rvDailyForecast);
        btnToggleUnit    = findViewById(R.id.btnToggleUnit);
        temperatureChart = findViewById(R.id.temperatureChart);
        sliderHigh       = findViewById(R.id.sliderHigh);
        sliderLow        = findViewById(R.id.sliderLow);
        tvHighValue      = findViewById(R.id.tvHighValue);
        tvLowValue       = findViewById(R.id.tvLowValue);
        btnLayerTemp     = findViewById(R.id.btnLayerTemp);
        btnLayerClouds   = findViewById(R.id.btnLayerClouds);
        btnLayerRain     = findViewById(R.id.btnLayerRain);
        btnLayerWind     = findViewById(R.id.btnLayerWind);
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(WeatherService.class);
    }

    private void setupChartScrollSync() {
        rvForecast.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int totalScrollX = 0;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                totalScrollX += dx;
                temperatureChart.setScrollOffset(totalScrollX);
            }
        });
    }

    // ─────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────

    private void checkPermissions() {
        // Xin quyền POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERM);
            }
        }

        // Xin quyền vị trí
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERM);
        } else {
            getLastLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOC_PERM
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }

        // NOTIF_PERM: không cần xử lý thêm,
        // WeatherNotificationHelper tự hoạt động khi được cấp quyền
    }

    // ─────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLon = location.getLongitude();
                saveLocation(currentLat, currentLon);
                fetchWeatherData(currentLat, currentLon);
                updateMapLocation(currentLat, currentLon);
                scheduleWeatherWorker();
            }
        });
    }

    // ─────────────────────────────────────────────
    // Threshold
    // ─────────────────────────────────────────────

    private void loadThresholds() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        thresholdHigh = prefs.getInt("threshold_high", 35);
        thresholdLow  = prefs.getInt("threshold_low", 10);
    }

    private void saveThresholds() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("threshold_high", thresholdHigh)
                .putInt("threshold_low", thresholdLow)
                .apply();
    }

    private void saveLocation(double lat, double lon) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("lat", (float) lat)
                .putFloat("lon", (float) lon)
                .apply();
    }

    private void setupSliders() {
        sliderHigh.setValue(thresholdHigh);
        sliderLow.setValue(thresholdLow);
        tvHighValue.setText(thresholdHigh + "°");
        tvLowValue.setText(thresholdLow + "°");

        sliderHigh.addOnChangeListener((slider, value, fromUser) -> {
            thresholdHigh = (int) value;
            tvHighValue.setText(thresholdHigh + "°");
            saveThresholds();
            Toast.makeText(this,
                    getString(R.string.threshold_saved), Toast.LENGTH_SHORT).show();
        });

        sliderLow.addOnChangeListener((slider, value, fromUser) -> {
            thresholdLow = (int) value;
            tvLowValue.setText(thresholdLow + "°");
            saveThresholds();
            Toast.makeText(this,
                    getString(R.string.threshold_saved), Toast.LENGTH_SHORT).show();
        });
    }

    // ─────────────────────────────────────────────
    // WorkManager — chạy nền mỗi 1 giờ
    // ─────────────────────────────────────────────

    private void scheduleWeatherWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WeatherWorker.class, 1, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest);
    }

    // ─────────────────────────────────────────────
    // API Calls
    // ─────────────────────────────────────────────

    private void fetchWeatherData(double lat, double lon) {
        String units = isCelsius ? "metric" : "imperial";

        // Thời tiết hiện tại
        weatherService.getCurrentWeather(lat, lon, API_KEY, units, "vi")
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call,
                                           Response<WeatherResponse> response) {
                        if (response.isSuccessful() && response.body() != null)
                            updateUI(response.body());
                    }

                    @Override
                    public void onFailure(Call<WeatherResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.error_fetch_weather),
                                Toast.LENGTH_SHORT).show();
                        Log.e("MainActivity", "Lỗi thời tiết hiện tại", t);
                    }
                });

        // Dự báo theo giờ + 5 ngày
        weatherService.getForecast(lat, lon, API_KEY, units, "vi")
                .enqueue(new Callback<ForecastResponse>() {
                    @Override
                    public void onResponse(Call<ForecastResponse> call,
                                           Response<ForecastResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ForecastResponse.ForecastItem> items = response.body().list;

                            // Dự báo theo giờ
                            rvForecast.setAdapter(new ForecastAdapter(items));

                            // Biểu đồ nhiệt độ
                            List<Float> temps = new ArrayList<>();
                            List<Float> probs = new ArrayList<>();
                            for (ForecastResponse.ForecastItem item : items) {
                                temps.add(item.main.temp);
                                probs.add(item.pop);
                            }
                            temperatureChart.setData(temps, probs);

                            // Dự báo 5 ngày
                            buildDailyForecast(items);
                        }
                    }

                    @Override
                    public void onFailure(Call<ForecastResponse> call, Throwable t) {
                        Log.e("MainActivity", "Lỗi dự báo", t);
                    }
                });
    }

    // ─────────────────────────────────────────────
    // UI Update
    // ─────────────────────────────────────────────

    private void updateUI(WeatherResponse weather) {
        tvCityName.setText(weather.name);
        tvTemperature.setText(Math.round(weather.main.temp) + (isCelsius ? "°C" : "°F"));
        tvDescription.setText(weather.weather.get(0).description);
        tvHumidity.setText(String.format(
                getString(R.string.format_humidity), weather.main.humidity));
        tvWindSpeed.setText(isCelsius
                ? String.format(getString(R.string.format_wind_ms), weather.wind.speed)
                : String.format(getString(R.string.format_wind_mph), weather.wind.speed));

        String iconUrl = "https://openweathermap.org/img/wn/"
                + weather.weather.get(0).icon + "@4x.png";
        Glide.with(this).load(iconUrl).into(ivWeatherIcon);

        // Chỉ kiểm tra ngưỡng khi đang dùng °C để tránh so sánh sai đơn vị
        if (isCelsius) checkAndNotify(weather);
    }

    // ─────────────────────────────────────────────
    // Notifications
    // ─────────────────────────────────────────────

    private void checkAndNotify(WeatherResponse weather) {
        int    currentTemp = Math.round(weather.main.temp);
        String city        = weather.name;
        String description = weather.weather.get(0).description;
        int    weatherId   = weather.weather.get(0).id;

        // ── Cảnh báo nhiệt độ cao ──
        if (currentTemp >= thresholdHigh) {
            WeatherNotificationHelper.show(
                    this,
                    getString(R.string.notif_title_heat),
                    getString(R.string.notif_msg_heat, city, currentTemp, thresholdHigh),
                    2001);
        }

        // ── Cảnh báo nhiệt độ thấp ──
        if (currentTemp <= thresholdLow) {
            WeatherNotificationHelper.show(
                    this,
                    getString(R.string.notif_title_cold),
                    getString(R.string.notif_msg_cold, city, currentTemp, thresholdLow),
                    2002);
        }

        // ── Giông bão (200–232) ──
        if (weatherId >= 200 && weatherId <= 232) {
            WeatherNotificationHelper.show(
                    this,
                    "⛈️ Cảnh Báo Giông Bão",
                    city + " đang có giông bão: " + description,
                    2003);
        }
        // ── Mưa rất to / mưa cực to (502, 503, 504, 522) ──
        else if (weatherId == 502 || weatherId == 503
                || weatherId == 504 || weatherId == 522) {
            WeatherNotificationHelper.show(
                    this,
                    "🌧️ Cảnh Báo Mưa Lớn",
                    city + " đang có mưa lớn: " + description,
                    2004);
        }
        // ── Mưa phùn / mưa nhẹ / mưa vừa (300–531) ──
        else if (weatherId >= 300 && weatherId <= 531) {
            WeatherNotificationHelper.show(
                    this,
                    "🌦️ Thông Báo Mưa",
                    city + " đang có mưa: " + description,
                    2005);
        }
        // ── Tuyết (600–622) ──
        else if (weatherId >= 600 && weatherId <= 622) {
            WeatherNotificationHelper.show(
                    this,
                    "❄️ Cảnh Báo Tuyết",
                    city + " đang có tuyết: " + description,
                    2006);
        }
        // ── Sương mù / khói / tầm nhìn kém (700–781) ──
        else if (weatherId >= 700 && weatherId <= 781) {
            WeatherNotificationHelper.show(
                    this,
                    "🌫️ Cảnh Báo Tầm Nhìn Kém",
                    city + " đang có: " + description,
                    2007);
        }
    }

    // ─────────────────────────────────────────────
    // Daily Forecast
    // ─────────────────────────────────────────────

    private void buildDailyForecast(List<ForecastResponse.ForecastItem> items) {
        Map<String, List<ForecastResponse.ForecastItem>> byDay = new LinkedHashMap<>();
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (ForecastResponse.ForecastItem item : items) {
            String key = keyFmt.format(new Date(item.dt * 1000L));
            if (!byDay.containsKey(key)) byDay.put(key, new ArrayList<>());
            byDay.get(key).add(item);
        }

        List<DailyForecastItem> dailyItems = new ArrayList<>();
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", new Locale("vi", "VN"));
        int index = 0;

        for (Map.Entry<String, List<ForecastResponse.ForecastItem>> entry : byDay.entrySet()) {
            List<ForecastResponse.ForecastItem> dayEntries = entry.getValue();

            float  maxTemp = Float.MIN_VALUE;
            float  minTemp = Float.MAX_VALUE;
            float  maxPop  = 0;
            String icon    = dayEntries.get(0).weather.get(0).icon;

            for (ForecastResponse.ForecastItem e : dayEntries) {
                if (e.main.temp > maxTemp) maxTemp = e.main.temp;
                if (e.main.temp < minTemp) minTemp = e.main.temp;
                if (e.pop > maxPop) {
                    maxPop = e.pop;
                    icon   = e.weather.get(0).icon;
                }
            }

            DailyForecastItem daily = new DailyForecastItem();
            daily.tempMax     = maxTemp;
            daily.tempMin     = minTemp;
            daily.icon        = icon;
            daily.rainPercent = Math.round(maxPop * 100);
            daily.dayName     = (index == 0)
                    ? "Hôm nay"
                    : formatDayName(entry.getKey(), dayFmt);

            dailyItems.add(daily);
            index++;
        }

        rvDailyForecast.setAdapter(new DailyForecastAdapter(dailyItems));
    }

    private String formatDayName(String dateKey, SimpleDateFormat dayFmt) {
        try {
            Date   date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey);
            String name = dayFmt.format(date);
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        } catch (Exception e) {
            return dateKey;
        }
    }

    // ─────────────────────────────────────────────
    // Google Maps
    // ─────────────────────────────────────────────

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        setupMapLayerButtons();
        switchWeatherLayer("temp_new");
    }

    private void updateMapLocation(double lat, double lon) {
        if (mMap == null) return;
        LatLng location = new LatLng(lat, lon);
        mMap.clear();
        mMap.addMarker(new MarkerOptions()
                .position(location)
                .title(getString(R.string.marker_current_location)));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 8));
        switchWeatherLayer("temp_new");
    }

    private void setupMapLayerButtons() {
        btnLayerTemp.setOnClickListener(v   -> switchWeatherLayer("temp_new"));
        btnLayerClouds.setOnClickListener(v -> switchWeatherLayer("clouds_new"));
        btnLayerRain.setOnClickListener(v   -> switchWeatherLayer("precipitation_new"));
        btnLayerWind.setOnClickListener(v   -> switchWeatherLayer("wind_new"));
    }

    private void switchWeatherLayer(String layerType) {
        if (mMap == null) return;

        // Xóa layer cũ
        if (currentTileOverlay != null) {
            currentTileOverlay.remove();
            currentTileOverlay = null;
        }

        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public URL getTileUrl(int x, int y, int zoom) {
                String s = String.format(
                        "https://tile.openweathermap.org/map/%s/%d/%d/%d.png?appid=%s",
                        layerType, zoom, x, y, API_KEY);
                try {
                    return new URL(s);
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }
        };

        currentTileOverlay = mMap.addTileOverlay(
                new TileOverlayOptions().tileProvider(tileProvider));
    }
}