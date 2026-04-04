package com.example.weathermoblieapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
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

import com.bumptech.glide.Glide;
import com.example.weathermoblieapp.api.WeatherService;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String API_KEY = "076a2fc17d5de3b400bb4eb9c216d6c1"; // Replace with your API Key
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FusedLocationProviderClient fusedLocationClient;
    private WeatherService weatherService;
    private GoogleMap mMap;

    private TextView tvCityName, tvTemperature, tvDescription, tvHumidity, tvWindSpeed;
    private ImageView ivWeatherIcon;
    private RecyclerView rvForecast;
    private Button btnToggleUnit;

    private boolean isCelsius = true;
    private double currentLat, currentLon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRetrofit();
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        checkLocationPermission();

        btnToggleUnit.setOnClickListener(v -> {
            isCelsius = !isCelsius;
            btnToggleUnit.setText(isCelsius ? "Switch to °F" : "Switch to °C");
            fetchWeatherData(currentLat, currentLon);
        });
    }

    private void initViews() {
        tvCityName = findViewById(R.id.tvCityName);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWindSpeed = findViewById(R.id.tvWindSpeed);
        ivWeatherIcon = findViewById(R.id.ivWeatherIcon);
        rvForecast = findViewById(R.id.rvForecast);
        btnToggleUnit = findViewById(R.id.btnToggleUnit);
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(WeatherService.class);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLon = location.getLongitude();
                    fetchWeatherData(currentLat, currentLon);
                    updateMapLocation(currentLat, currentLon);
                }
            });
        }
    }

    private void fetchWeatherData(double lat, double lon) {
        String units = isCelsius ? "metric" : "imperial";
        
        // Current Weather
        weatherService.getCurrentWeather(lat, lon, API_KEY, units).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error fetching weather", Toast.LENGTH_SHORT).show();
            }
        });

        // Forecast
        weatherService.getForecast(lat, lon, API_KEY, units).enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ForecastAdapter adapter = new ForecastAdapter(response.body().list);
                    rvForecast.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Call<ForecastResponse> call, Throwable t) {
                Log.e("MainActivity", "Forecast error", t);
            }
        });
    }

    private void updateUI(WeatherResponse weather) {
        tvCityName.setText(weather.name);
        tvTemperature.setText(Math.round(weather.main.temp) + (isCelsius ? "°C" : "°F"));
        tvDescription.setText(weather.weather.get(0).description);
        tvHumidity.setText("Humidity: " + weather.main.humidity + "%");
        tvWindSpeed.setText("Wind: " + weather.wind.speed + (isCelsius ? " m/s" : " mph"));

        String iconUrl = "https://openweathermap.org/img/wn/" + weather.weather.get(0).icon + "@4x.png";
        Glide.with(this).load(iconUrl).into(ivWeatherIcon);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        addWeatherLayer("temp_new"); // Default layer
    }

    private void updateMapLocation(double lat, double lon) {
        if (mMap != null) {
            LatLng location = new LatLng(lat, lon);
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(location).title("Current Location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 8));
            addWeatherLayer("temp_new");
        }
    }

    private void addWeatherLayer(String layerType) {
        if (mMap == null) return;
        
        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public URL getTileUrl(int x, int y, int zoom) {
                String s = String.format("https://tile.openweathermap.org/map/%s/%d/%d/%d.png?appid=%s",
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            }
        }
    }
}
