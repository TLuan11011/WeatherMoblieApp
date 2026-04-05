package com.example.weathermoblieapp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

    private static final String API_KEY = "076a2fc17d5de3b400bb4eb9c216d6c1";
    private static final int    LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final String PREFS_NAME = "weather_prefs";
    private static final String WORK_TAG   = "weather_check";

    private FusedLocationProviderClient fusedLocationClient;
    private WeatherService weatherService;
    private GoogleMap mMap;

    private TextView tvCityName, tvTemperature, tvDescription, tvHumidity, tvWindSpeed;
    private TextView tvHighValue, tvLowValue;
    private ImageView ivWeatherIcon;
    private RecyclerView rvForecast, rvDailyForecast;
    private Button btnToggleUnit;
    private TemperatureChartView temperatureChart;
    private Slider sliderHigh, sliderLow;

    private boolean isCelsius = true;
    private double currentLat, currentLon;
    private int thresholdHigh = 35;
    private int thresholdLow  = 10;

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

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        checkLocationPermission();

        btnToggleUnit.setOnClickListener(v -> {
            isCelsius = !isCelsius;
            btnToggleUnit.setText(isCelsius
                    ? getString(R.string.btn_switch_to_f)
                    : getString(R.string.btn_switch_to_c));
            fetchWeatherData(currentLat, currentLon);
        });
    }

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
    }

    private void loadThresholds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        thresholdHigh = prefs.getInt("threshold_high", 35);
        thresholdLow  = prefs.getInt("threshold_low", 10);
    }

    private void saveThresholds() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("threshold_high", thresholdHigh)
                .putInt("threshold_low", thresholdLow)
                .apply();
    }

    private void saveLocation(double lat, double lon) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
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
            Toast.makeText(this, getString(R.string.threshold_saved), Toast.LENGTH_SHORT).show();
        });

        sliderLow.addOnChangeListener((slider, value, fromUser) -> {
            thresholdLow = (int) value;
            tvLowValue.setText(thresholdLow + "°");
            saveThresholds();
            Toast.makeText(this, getString(R.string.threshold_saved), Toast.LENGTH_SHORT).show();
        });
    }

    private void scheduleWeatherWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WeatherWorker.class, 1, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
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

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(WeatherService.class);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
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
    }

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
                            ForecastAdapter adapter = new ForecastAdapter(items);
                            rvForecast.setAdapter(adapter);

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

    private void checkAndNotify(WeatherResponse weather) {
        int currentTemp = Math.round(weather.main.temp);
        String city     = weather.name;

        if (currentTemp >= thresholdHigh) {
            String title = getString(R.string.notif_title_heat);
            String msg   = getString(R.string.notif_msg_heat, city, currentTemp, thresholdHigh);
            WeatherNotificationHelper.show(this, title, msg, 2001);
        } else if (currentTemp <= thresholdLow) {
            String title = getString(R.string.notif_title_cold);
            String msg   = getString(R.string.notif_msg_cold, city, currentTemp, thresholdLow);
            WeatherNotificationHelper.show(this, title, msg, 2002);
        }
    }

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

            float maxTemp = Float.MIN_VALUE;
            float minTemp = Float.MAX_VALUE;
            float maxPop  = 0;
            String icon   = dayEntries.get(0).weather.get(0).icon;

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

            if (index == 0) {
                daily.dayName = "Hôm nay";
            } else {
                try {
                    Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .parse(entry.getKey());
                    String name = dayFmt.format(date);
                    daily.dayName = name.substring(0, 1).toUpperCase() + name.substring(1);
                } catch (Exception e) {
                    daily.dayName = entry.getKey();
                }
            }

            dailyItems.add(daily);
            index++;
        }

        rvDailyForecast.setAdapter(new DailyForecastAdapter(dailyItems));
    }

    private void updateUI(WeatherResponse weather) {
        tvCityName.setText(weather.name);
        tvTemperature.setText(Math.round(weather.main.temp) + (isCelsius ? "°C" : "°F"));
        tvDescription.setText(weather.weather.get(0).description);
        tvHumidity.setText(String.format(getString(R.string.format_humidity), weather.main.humidity));
        tvWindSpeed.setText(isCelsius
                ? String.format(getString(R.string.format_wind_ms), weather.wind.speed)
                : String.format(getString(R.string.format_wind_mph), weather.wind.speed));

        String iconUrl = "https://openweathermap.org/img/wn/"
                + weather.weather.get(0).icon + "@4x.png";
        Glide.with(this).load(iconUrl).into(ivWeatherIcon);

        // Kiểm tra ngưỡng và gửi thông báo nếu cần
        if (isCelsius) checkAndNotify(weather);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        addWeatherLayer("temp_new");
    }

    private void updateMapLocation(double lat, double lon) {
        if (mMap != null) {
            LatLng location = new LatLng(lat, lon);
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title(getString(R.string.marker_current_location)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 8));
            addWeatherLayer("temp_new");
        }
    }

    private void addWeatherLayer(String layerType) {
        if (mMap == null) return;
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
        mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }
    }
}