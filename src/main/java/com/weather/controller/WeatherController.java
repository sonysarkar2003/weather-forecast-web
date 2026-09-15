package com.weather.controller;

import com.weather.dto.LocationDTO;
import com.weather.dto.WeatherResponse;
import com.weather.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/live")
    public ResponseEntity<WeatherResponse> getLiveWeather(
            @RequestParam(name = "lat", defaultValue = "28.6139") double lat,
            @RequestParam(name = "lon", defaultValue = "77.2090") double lon) {
        WeatherResponse response = weatherService.getWeatherByCoordinates(lat, lon, null, null, null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<WeatherResponse> getWeatherByCity(
            @RequestParam(name = "city") String city) {
        WeatherResponse response = weatherService.getWeatherByCity(city);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<LocationDTO>> autocomplete(
            @RequestParam(name = "q") String query) {
        List<LocationDTO> suggestions = weatherService.searchCities(query);
        return ResponseEntity.ok(suggestions);
    }
}
