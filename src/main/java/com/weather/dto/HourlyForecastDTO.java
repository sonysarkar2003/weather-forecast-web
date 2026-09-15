package com.weather.dto;

public class HourlyForecastDTO {
    private String time;
    private double temperature;
    private int humidity;
    private int rainProbability;
    private String weatherCondition;
    private String weatherIcon;

    public HourlyForecastDTO() {}

    public HourlyForecastDTO(String time, double temperature, int humidity, int rainProbability, String weatherCondition, String weatherIcon) {
        this.time = time;
        this.temperature = temperature;
        this.humidity = humidity;
        this.rainProbability = rainProbability;
        this.weatherCondition = weatherCondition;
        this.weatherIcon = weatherIcon;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public int getRainProbability() { return rainProbability; }
    public void setRainProbability(int rainProbability) { this.rainProbability = rainProbability; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }

    public String getWeatherIcon() { return weatherIcon; }
    public void setWeatherIcon(String weatherIcon) { this.weatherIcon = weatherIcon; }
}
