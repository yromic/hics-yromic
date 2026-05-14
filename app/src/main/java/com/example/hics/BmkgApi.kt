package com.example.hics.weather

import retrofit2.Call
import retrofit2.http.GET

interface BmkgApi {

    @GET("DataMKG/MEWS/DigitalForecast/DigitalForecast-JawaTengah.xml")
    fun getWeather(): Call<okhttp3.ResponseBody>
}