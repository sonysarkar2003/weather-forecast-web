package com.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${weather.api.key:}")
    private String weatherApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherResponse getWeatherByCoordinates(double lat, double lon, String overrideCityName, String overrideRegion, String overrideCountry) {
        if (hasWeatherApiKey()) {
            try {
                return getWeatherFromWeatherApi(lat + "," + lon, overrideCityName, overrideRegion, overrideCountry);
            } catch (Exception e) {
                System.err.println("WeatherAPI.com request failed, falling back to Open-Meteo: " + e.getMessage());
            }
        }
        return getWeatherFromOpenMeteo(lat, lon, overrideCityName, overrideRegion, overrideCountry);
    }

    public WeatherResponse getWeatherByCity(String city) {
        if (hasWeatherApiKey()) {
            try {
                return getWeatherFromWeatherApi(city, null, null, null);
            } catch (Exception e) {
                System.err.println("WeatherAPI.com city search failed, falling back to Open-Meteo: " + e.getMessage());
            }
        }
        return getWeatherByCityOpenMeteo(city);
    }

    public List<LocationDTO> searchCities(String query) {
        if (hasWeatherApiKey()) {
            try {
                return searchCitiesWeatherApi(query);
            } catch (Exception ignored) {}
        }
        return searchCitiesOpenMeteo(query);
    }

    private boolean hasWeatherApiKey() {
        return weatherApiKey != null && !weatherApiKey.trim().isEmpty() && !weatherApiKey.equalsIgnoreCase("YOUR_API_KEY");
    }

    // ==========================================
    // WeatherAPI.com Integration Implementation
    // ==========================================
    private WeatherResponse getWeatherFromWeatherApi(String query, String overrideCityName, String overrideRegion, String overrideCountry) {
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weatherapi.com/v1/forecast.json")
                .queryParam("key", weatherApiKey.trim())
                .queryParam("q", query)
                .queryParam("days", 7)
                .queryParam("aqi", "no")
                .queryParam("alerts", "no")
                .build().toUriString();

        String responseJson = restTemplate.getForObject(url, String.class);
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // 1. Location
            JsonNode locNode = root.path("location");
            LocationDTO location = new LocationDTO();
            location.setName(overrideCityName != null ? overrideCityName : locNode.path("name").asText());
            location.setRegion(overrideRegion != null ? overrideRegion : locNode.path("region").asText(""));
            location.setCountry(overrideCountry != null ? overrideCountry : locNode.path("country").asText(""));
            location.setLatitude(locNode.path("lat").asDouble(0.0));
            location.setLongitude(locNode.path("lon").asDouble(0.0));

            // 2. Current Weather
            JsonNode currNode = root.path("current");
            CurrentWeatherDTO currentWeather = new CurrentWeatherDTO();
            currentWeather.setTemperature(currNode.path("temp_c").asDouble(0.0));
            currentWeather.setHumidity(currNode.path("humidity").asInt(0));
            currentWeather.setFeelsLike(currNode.path("feelslike_c").asDouble(currentWeather.getTemperature()));
            currentWeather.setWindSpeed(currNode.path("wind_kph").asDouble(0.0));
            currentWeather.setDay(currNode.path("is_day").asInt(1) == 1);
            
            JsonNode condNode = currNode.path("condition");
            String condText = condNode.path("text").asText("Clear");
            int code = condNode.path("code").asInt(1000);
            currentWeather.setWeatherCode(code);
            currentWeather.setWeatherCondition(condText);
            currentWeather.setWeatherIcon(mapWeatherApiCodeToIcon(code, currentWeather.isDay()));
            currentWeather.setTimestamp(currNode.path("last_updated").asText(LocalDateTime.now().toString()));

            // 3. Hourly & Daily Forecast
            List<HourlyForecastDTO> hourlyList = new ArrayList<>();
            List<DailyForecastDTO> dailyList = new ArrayList<>();

            JsonNode forecastDays = root.path("forecast").path("forecastday");

            // Extract Current Rain Probability % from Today's Day or First Hour
            if (forecastDays.isArray() && forecastDays.size() > 0) {
                JsonNode todayNode = forecastDays.get(0);
                int todayRainProb = todayNode.path("day").path("daily_chance_of_rain").asInt(0);
                
                // Hour 0 or current hour
                JsonNode hours = todayNode.path("hour");
                if (hours.isArray() && hours.size() > 0) {
                    todayRainProb = hours.get(0).path("chance_of_rain").asInt(todayRainProb);
                }
                currentWeather.setRainProbability(todayRainProb);

                // Map 24 Hourly Items from Today's forecastday
                int hourlyLimit = Math.min(24, hours.size());
                for (int i = 0; i < hourlyLimit; i++) {
                    JsonNode h = hours.get(i);
                    String rawTime = h.path("time").asText();
                    String formattedTime = formatHourlyTime(rawTime);
                    double temp = h.path("temp_c").asDouble();
                    int hum = h.path("humidity").asInt();
                    int rainProb = h.path("chance_of_rain").asInt();
                    int hCode = h.path("condition").path("code").asInt(1000);
                    String hCond = h.path("condition").path("text").asText("Clear");

                    hourlyList.add(new HourlyForecastDTO(
                            formattedTime,
                            temp,
                            hum,
                            rainProb,
                            hCond,
                            mapWeatherApiCodeToIcon(hCode, true)
                    ));
                }
            }

            // Map Daily Items
            if (forecastDays.isArray()) {
                int dailyLimit = Math.min(7, forecastDays.size());
                for (int i = 0; i < dailyLimit; i++) {
                    JsonNode d = forecastDays.get(i);
                    String rawDate = d.path("date").asText();
                    String dayName = formatDayName(rawDate, i);
                    JsonNode dayInfo = d.path("day");

                    double maxT = dayInfo.path("maxtemp_c").asDouble();
                    double minT = dayInfo.path("mintemp_c").asDouble();
                    int rainProb = dayInfo.path("daily_chance_of_rain").asInt();
                    int dCode = dayInfo.path("condition").path("code").asInt(1000);
                    String dCond = dayInfo.path("condition").path("text").asText();

                    dailyList.add(new DailyForecastDTO(
                            rawDate,
                            dayName,
                            maxT,
                            minT,
                            rainProb,
                            dCond,
                            mapWeatherApiCodeToIcon(dCode, true)
                    ));
                }
            }

            return new WeatherResponse(location, currentWeather, hourlyList, dailyList);

        } catch (Exception e) {
            throw new RuntimeException("Error parsing WeatherAPI response: " + e.getMessage(), e);
        }
    }

    private List<LocationDTO> searchCitiesWeatherApi(String query) {
        List<LocationDTO> resultsList = new ArrayList<>();
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weatherapi.com/v1/search.json")
                .queryParam("key", weatherApiKey.trim())
                .queryParam("q", query)
                .build().toUriString();

        String response = restTemplate.getForObject(url, String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String name = node.path("name").asText();
                    String region = node.path("region").asText("");
                    String country = node.path("country").asText("");
                    double lat = node.path("lat").asDouble();
                    double lon = node.path("lon").asDouble();

                    resultsList.add(new LocationDTO(name, region, country, lat, lon));
                }
            }
        } catch (Exception ignored) {}
        return resultsList;
    }

    private String mapWeatherApiCodeToIcon(int code, boolean isDay) {
        // WeatherAPI condition codes
        return switch (code) {
            case 1000 -> isDay ? "sun" : "moon"; // Sunny / Clear
            case 1003, 1006 -> isDay ? "cloud-sun" : "cloud-moon"; // Partly cloudy
            case 1009, 1030 -> "cloud"; // Overcast / Mist
            case 1135, 1147 -> "smog"; // Fog
            case 1063, 1150, 1153, 1180, 1183 -> "cloud-rain"; // Light rain / drizzle
            case 1186, 1189, 1192, 1195, 1240, 1243 -> "cloud-showers-heavy"; // Heavy rain / showers
            case 1066, 1114, 1210, 1213, 1216, 1219, 1222, 1225 -> "snowflake"; // Snow
            case 1087, 1273, 1276, 1279, 1282 -> "cloud-bolt"; // Thunderstorm
            default -> isDay ? "sun" : "moon";
        };
    }

    // ==========================================
    // Open-Meteo Fallback Implementation
    // ==========================================
    private WeatherResponse getWeatherFromOpenMeteo(double lat, double lon, String overrideCityName, String overrideRegion, String overrideCountry) {
        try {
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

            int currentRainProb = 0;
            if (hourly.has("precipitation_probability") && hourly.path("precipitation_probability").isArray()) {
                JsonNode probArray = hourly.path("precipitation_probability");
                if (probArray.size() > 0) {
                    currentRainProb = probArray.get(0).asInt(0);
                }
            }
            currentWeather.setRainProbability(currentRainProb);

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
            throw new RuntimeException("Failed to fetch weather data from Open-Meteo: " + e.getMessage(), e);
        }
    }

    private WeatherResponse getWeatherByCityOpenMeteo(String city) {
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

            return getWeatherFromOpenMeteo(lat, lon, name, region, country);

        } catch (Exception e) {
            throw new RuntimeException("City search error: " + e.getMessage(), e);
        }
    }

    private List<LocationDTO> searchCitiesOpenMeteo(String query) {
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

    private String formatHourlyTime(String rawTime) {
        try {
            if (rawTime.contains(" ")) {
                LocalDateTime dt = LocalDateTime.parse(rawTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                return dt.format(DateTimeFormatter.ofPattern("ha")).toLowerCase();
            }
            LocalDateTime dt = LocalDateTime.parse(rawTime);
            return dt.format(DateTimeFormatter.ofPattern("ha")).toLowerCase();
        } catch (Exception e) {
            return rawTime;
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
