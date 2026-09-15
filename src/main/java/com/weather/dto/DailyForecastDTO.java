package com.weather.dto;

public class DailyForecastDTO {
    private String date;
    private String dayName;
    private double tempMax;
    private double tempMin;
    private int maxRainProbability;
    private String weatherCondition;
    private String weatherIcon;

    public DailyForecastDTO() {}

    public DailyForecastDTO(String date, String dayName, double tempMax, double tempMin, int maxRainProbability, String weatherCondition, String weatherIcon) {
        this.date = date;
        this.dayName = dayName;
        this.tempMax = tempMax;
        this.tempMin = tempMin;
        this.maxRainProbability = maxRainProbability;
        this.weatherCondition = weatherCondition;
        this.weatherIcon = weatherIcon;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDayName() { return dayName; }
    public void setDayName(String dayName) { this.dayName = dayName; }

    public double getTempMax() { return tempMax; }
    public void setTempMax(double tempMax) { this.tempMax = tempMax; }

    public double getTempMin() { return tempMin; }
    public void setTempMin(double tempMin) { this.tempMin = tempMin; }

    public int getMaxRainProbability() { return maxRainProbability; }
    public void setMaxRainProbability(int maxRainProbability) { this.maxRainProbability = maxRainProbability; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }

    public String getWeatherIcon() { return weatherIcon; }
    public void setWeatherIcon(String weatherIcon) { this.weatherIcon = weatherIcon; }
}
