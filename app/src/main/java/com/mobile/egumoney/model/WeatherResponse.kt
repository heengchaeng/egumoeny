package com.mobile.egumoney.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("weather") val weatherList: List<WeatherInfo>,
    @SerializedName("main") val mainInfo: MainInfo,
    @SerializedName("name") val cityName: String
)

data class WeatherInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("main") val main: String, // e.g. "Rain", "Clouds", "Clear"
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String
)

data class MainInfo(
    @SerializedName("temp") val temp: Double
)
