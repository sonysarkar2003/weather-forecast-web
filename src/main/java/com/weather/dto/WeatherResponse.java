package com.weather.dto;

import java.util.List;

public class WeatherResponse {
    private LocationDTO location;
    private CurrentWeatherDTO current;
    private List<HourlyForecastDTO> hourly;
    private List<DailyForecastDTO> daily;

    public WeatherResponse() {}

    public WeatherResponse(LocationDTO location, CurrentWeatherDTO current, List<HourlyForecastDTO> hourly, List<DailyForecastDTO> daily) {
        this.location = location;
        this.current = current;
        this.hourly = hourly;
        this.daily = daily;
    }

    public LocationDTO getLocation() { return location; }
    public void setLocation(LocationDTO location) { this.location = location; }

    public CurrentWeatherDTO getCurrent() { return current; }
    public void setCurrent(CurrentWeatherDTO current) { this.current = current; }

    public List<HourlyForecastDTO> getHourly() { return hourly; }
    public void setHourly(List<HourlyForecastDTO> hourly) { this.hourly = hourly; }

    public List<DailyForecastDTO> getDaily() { return daily; }
    public void setDaily(List<DailyForecastDTO> daily) { this.daily = daily; }
}
