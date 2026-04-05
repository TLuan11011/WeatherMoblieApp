package com.example.weathermoblieapp.api;

import com.example.weathermoblieapp.model.ForecastResponse;
import com.example.weathermoblieapp.model.WeatherResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherService {
    @GET("weather")
    Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );

    @GET("forecast")
    Call<ForecastResponse> getForecast(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
}