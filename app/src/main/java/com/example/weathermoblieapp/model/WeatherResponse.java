package com.example.weathermoblieapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WeatherResponse {

    @SerializedName("main")
    public Main main;

    @SerializedName("weather")
    public List<Weather> weather;

    @SerializedName("wind")
    public Wind wind;

    @SerializedName("name")
    public String name;

    public static class Main {
        @SerializedName("temp")
        public float temp;

        @SerializedName("humidity")
        public int humidity;
    }

    public static class Weather {
        @SerializedName("id")
        public int id;

        @SerializedName("description")
        public String description;

        @SerializedName("icon")
        public String icon;
    }

    public static class Wind {
        @SerializedName("speed")
        public float speed;
    }
}