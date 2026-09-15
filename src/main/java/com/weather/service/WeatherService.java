package com.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class WeatherService {

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherResponse getWeatherByCoordinates(double lat, double lon, String overrideCityName, String overrideRegion, String overrideCountry) {
        try {
            // 1. Fetch Forecast from Open-Meteo API
            String forecastUrl = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                    .queryParam("latitude", lat)
                    .queryParam("longitude", lon)
                    .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,apparent_temperature,is_day")
                    .queryParam("hourly", "temperature_2m,relative_humidity_2m,precipitation_probability,weather_code")
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                    .queryParam("timezone", "auto")
                    .build().toUriString();

            String responseJson = restTemplate.getForObject(forecastUrl, String.class);
            JsonNode root = objectMapper.readTree(responseJson);

            // 2. Determine Location Details if not overridden
            LocationDTO location = new LocationDTO();
            location.setLatitude(lat);
            location.setLongitude(lon);

            if (overrideCityName != null && !overrideCityName.isEmpty()) {
                location.setName(overrideCityName);
                location.setRegion(overrideRegion != null ? overrideRegion : "");
                location.setCountry(overrideCountry != null ? overrideCountry : "");
            } else {
                LocationDTO reverseLoc = reverseGeocode(lat, lon);
                location.setName(reverseLoc.getName());
                location.setRegion(reverseLoc.getRegion());
                location.setCountry(reverseLoc.getCountry());
            }

            // 3. Map Current Weather
            JsonNode current = root.path("current");
            JsonNode hourly = root.path("hourly");

            CurrentWeatherDTO currentWeather = new CurrentWeatherDTO();
            currentWeather.setTemperature(current.path("temperature_2m").asDouble(0.0));
            currentWeather.setHumidity(current.path("relative_humidity_2m").asInt(0));
            currentWeather.setFeelsLike(current.path("apparent_temperature").asDouble(currentWeather.getTemperature()));
            currentWeather.setWindSpeed(current.path("wind_speed_10m").asDouble(0.0));
            currentWeather.setDay(current.path("is_day").asInt(1) == 1);
            int code = current.path("weather_code").asInt(0);
            currentWeather.setWeatherCode(code);
            currentWeather.setWeatherCondition(getConditionName(code));
            currentWeather.setWeatherIcon(getWeatherIcon(code, currentWeather.isDay()));
            currentWeather.setTimestamp(current.path("time").asText(LocalDateTime.now().toString()));

            // Current Rain Probability percentage from current hour index
            int currentRainProb = 0;
            if (hourly.has("precipitation_probability") && hourly.path("precipitation_probability").isArray()) {
                JsonNode probArray = hourly.path("precipitation_probability");
                if (probArray.size() > 0) {
                    currentRainProb = probArray.get(0).asInt(0);
                }
            }
            currentWeather.setRainProbability(currentRainProb);

            // 4. Map Hourly Forecast (next 24 hours)
            List<HourlyForecastDTO> hourlyList = new ArrayList<>();
            JsonNode hourlyTime = hourly.path("time");
            JsonNode hourlyTemp = hourly.path("temperature_2m");
            JsonNode hourlyHum = hourly.path("relative_humidity_2m");
            JsonNode hourlyProb = hourly.path("precipitation_probability");
            JsonNode hourlyCode = hourly.path("weather_code");

            int hourlyLimit = Math.min(24, hourlyTime.size());
            for (int i = 0; i < hourlyLimit; i++) {
                String rawTime = hourlyTime.get(i).asText();
                String formattedTime = formatHourlyTime(rawTime);
                double t = hourlyTemp.get(i).asDouble();
                int h = hourlyHum.get(i).asInt();
                int p = hourlyProb.get(i).asInt();
                int c = hourlyCode.get(i).asInt();

                hourlyList.add(new HourlyForecastDTO(
                        formattedTime,
                        t,
                        h,
                        p,
                        getConditionName(c),
                        getWeatherIcon(c, true)
                ));
            }

            // 5. Map Daily Forecast (next 7 days)
            List<DailyForecastDTO> dailyList = new ArrayList<>();
            JsonNode daily = root.path("daily");
            JsonNode dailyTime = daily.path("time");
            JsonNode dailyCode = daily.path("weather_code");
            JsonNode dailyMaxTemp = daily.path("temperature_2m_max");
            JsonNode dailyMinTemp = daily.path("temperature_2m_min");
            JsonNode dailyProb = daily.path("precipitation_probability_max");

            int dailyLimit = Math.min(7, dailyTime.size());
            for (int i = 0; i < dailyLimit; i++) {
                String rawDate = dailyTime.get(i).asText();
                String dayName = formatDayName(rawDate, i);
                double maxT = dailyMaxTemp.get(i).asDouble();
                double minT = dailyMinTemp.get(i).asDouble();
                int prob = dailyProb.get(i).asInt();
                int c = dailyCode.get(i).asInt();

                dailyList.add(new DailyForecastDTO(
                        rawDate,
                        dayName,
                        maxT,
                        minT,
                        prob,
                        getConditionName(c),
                        getWeatherIcon(c, true)
                ));
            }

            return new WeatherResponse(location, currentWeather, hourlyList, dailyList);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch weather data: " + e.getMessage(), e);
        }
    }

    public WeatherResponse getWeatherByCity(String city) {
        try {
            String geoUrl = UriComponentsBuilder.fromHttpUrl("https://geocoding-api.open-meteo.com/v1/search")
                    .queryParam("name", city)
                    .queryParam("count", 1)
                    .queryParam("language", "en")
                    .build().toUriString();

            String response = restTemplate.getForObject(geoUrl, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (!root.has("results") || root.path("results").isEmpty()) {
                throw new IllegalArgumentException("Location not found for city: " + city);
            }

            JsonNode firstResult = root.path("results").get(0);
            double lat = firstResult.path("latitude").asDouble();
            double lon = firstResult.path("longitude").asDouble();
            String name = firstResult.path("name").asText();
            String region = firstResult.path("admin1").asText("");
            String country = firstResult.path("country").asText("");

            return getWeatherByCoordinates(lat, lon, name, region, country);

        } catch (Exception e) {
            throw new RuntimeException("City search error: " + e.getMessage(), e);
        }
    }

    public List<LocationDTO> searchCities(String query) {
        List<LocationDTO> resultsList = new ArrayList<>();
        if (query == null || query.trim().length() < 2) {
            return resultsList;
        }

        try {
            String geoUrl = UriComponentsBuilder.fromHttpUrl("https://geocoding-api.open-meteo.com/v1/search")
                    .queryParam("name", query)
                    .queryParam("count", 5)
                    .queryParam("language", "en")
                    .build().toUriString();

            String response = restTemplate.getForObject(geoUrl, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("results") && root.path("results").isArray()) {
                for (JsonNode node : root.path("results")) {
                    String name = node.path("name").asText();
                    String region = node.path("admin1").asText("");
                    String country = node.path("country").asText("");
                    double lat = node.path("latitude").asDouble();
                    double lon = node.path("longitude").asDouble();

                    resultsList.add(new LocationDTO(name, region, country, lat, lon));
                }
            }
        } catch (Exception ignored) {
        }
        return resultsList;
    }

    private LocationDTO reverseGeocode(double lat, double lon) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.bigdatacloud.net/data/reverse-geocode-client")
                    .queryParam("latitude", lat)
                    .queryParam("longitude", lon)
                    .queryParam("localityLanguage", "en")
                    .build().toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(response);

            String city = node.path("city").asText("");
            if (city.isEmpty()) city = node.path("locality").asText("");
            if (city.isEmpty()) city = node.path("principalSubdivision").asText("Your Location");

            String region = node.path("principalSubdivision").asText("");
            String country = node.path("countryName").asText("");

            return new LocationDTO(city, region, country, lat, lon);
        } catch (Exception e) {
            return new LocationDTO("Current Location", "", "", lat, lon);
        }
    }

    private String getConditionName(int code) {
        return switch (code) {
            case 0 -> "Clear Sky";
            case 1 -> "Mainly Clear";
            case 2 -> "Partly Cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing Drizzle";
            case 61, 63 -> "Light Rain";
            case 65 -> "Heavy Rain";
            case 66, 67 -> "Freezing Rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow Grains";
            case 80, 81, 82 -> "Rain Showers";
            case 85, 86 -> "Snow Showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with Hail";
            default -> "Fair";
        };
    }

    private String getWeatherIcon(int code, boolean isDay) {
        return switch (code) {
            case 0 -> isDay ? "sun" : "moon";
            case 1, 2 -> isDay ? "cloud-sun" : "cloud-moon";
            case 3 -> "cloud";
            case 45, 48 -> "smog";
            case 51, 53, 55, 56, 57 -> "cloud-rain";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "cloud-showers-heavy";
            case 71, 73, 75, 77, 85, 86 -> "snowflake";
            case 95, 96, 99 -> "cloud-bolt";
            default -> "sun";
        };
    }

    private String formatHourlyTime(String isoString) {
        try {
            LocalDateTime dt = LocalDateTime.parse(isoString);
            return dt.format(DateTimeFormatter.ofPattern("ha")).toLowerCase();
        } catch (Exception e) {
            return isoString;
        }
    }

    private String formatDayName(String dateString, int index) {
        if (index == 0) return "Today";
        try {
            LocalDate date = LocalDate.parse(dateString);
            return date.getDayOfWeek().name().substring(0, 3);
        } catch (Exception e) {
            return dateString;
        }
    }
}
