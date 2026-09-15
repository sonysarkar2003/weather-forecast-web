package com.weather.dto;

public class CurrentWeatherDTO {
    private double temperature;
    private int humidity; // Percentage %
    private int rainProbability; // Percentage %
    private int weatherCode;
    private String weatherCondition;
    private String weatherIcon;
    private double windSpeed;
    private double feelsLike;
    private boolean isDay;
    private String timestamp;

    public CurrentWeatherDTO() {}

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public int getRainProbability() { return rainProbability; }
    public void setRainProbability(int rainProbability) { this.rainProbability = rainProbability; }

    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }

    public String getWeatherIcon() { return weatherIcon; }
    public void setWeatherIcon(String weatherIcon) { this.weatherIcon = weatherIcon; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    public double getFeelsLike() { return feelsLike; }
    public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }

    public boolean isDay() { return isDay; }
    public void setDay(boolean day) { isDay = day; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
