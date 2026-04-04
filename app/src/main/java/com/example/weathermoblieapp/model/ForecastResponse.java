package com.example.weathermoblieapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ForecastResponse {
    @SerializedName("list")
    public List<ForecastItem> list;

    public static class ForecastItem {
        @SerializedName("dt")
        public long dt;
        @SerializedName("main")
        public WeatherResponse.Main main;
        @SerializedName("weather")
        public List<WeatherResponse.Weather> weather;
        @SerializedName("dt_txt")
        public String dtTxt;
    }
}
