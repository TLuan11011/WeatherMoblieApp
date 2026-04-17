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

        @SerializedName("pop")
        public float pop;

        @SerializedName("rain")
        public Rain rain;

        public static class Rain {
            @SerializedName("3h")
            public float threeHour; // mm mưa trong 3 giờ
        }

        // Lấy mm mưa, trả về 0 nếu không có mưa
        public float getRainMm() {
            return (rain != null) ? rain.threeHour : 0f;
        }
    }
}