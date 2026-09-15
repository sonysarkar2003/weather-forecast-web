document.addEventListener('DOMContentLoaded', () => {
    // State Variables
    let currentUnit = 'C'; // 'C' or 'F'
    let rawWeatherData = null;
    let debounceTimer = null;

    // DOM Elements
    const mainContent = document.getElementById('mainContent');
    const loadingState = document.getElementById('loadingState');
    const errorState = document.getElementById('errorState');
    const errorMessage = document.getElementById('errorMessage');
    const retryBtn = document.getElementById('retryBtn');
    const weatherDashboard = document.getElementById('weatherDashboard');

    // Header & Search
    const citySearchInput = document.getElementById('citySearchInput');
    const clearSearchBtn = document.getElementById('clearSearchBtn');
    const autocompleteDropdown = document.getElementById('autocompleteDropdown');
    const geoLocateBtn = document.getElementById('geoLocateBtn');
    const unitToggleBtns = document.querySelectorAll('.unit-btn');
    const refreshDataBtn = document.getElementById('refreshDataBtn');
    const cityChips = document.querySelectorAll('.city-chip');

    // Location Info
    const cityNameEl = document.querySelector('#cityName span');
    const regionCountryEl = document.getElementById('regionCountry');
    const latValEl = document.getElementById('latVal');
    const lonValEl = document.getElementById('lonVal');
    const lastUpdatedTimeEl = document.getElementById('lastUpdatedTime');

    // Current Weather Hero
    const heroWeatherIcon = document.getElementById('heroWeatherIcon');
    const currentTempEl = document.getElementById('currentTemp');
    const displayUnitEl = document.getElementById('displayUnit');
    const conditionTextEl = document.getElementById('conditionText');
    const feelsLikeTempEl = document.getElementById('feelsLikeTemp');
    const feelsUnitEls = document.querySelectorAll('.feels-unit');

    // Core Metrics
    const humidityValEl = document.getElementById('humidityVal');
    const humidityBarEl = document.getElementById('humidityBar');
    const humidityStatusBadge = document.getElementById('humidityStatusBadge');
    const humiditySubtext = document.getElementById('humiditySubtext');

    const rainProbValEl = document.getElementById('rainProbVal');
    const rainProbBarEl = document.getElementById('rainProbBar');
    const rainStatusBadge = document.getElementById('rainStatusBadge');
    const rainProbSubtext = document.getElementById('rainProbSubtext');

    const windSpeedValEl = document.getElementById('windSpeedVal');

    // Containers
    const hourlyContainer = document.getElementById('hourlyContainer');
    const dailyContainer = document.getElementById('dailyContainer');

    // Initialize App
    init();

    function init() {
        setupEventListeners();
        fetchLiveLocationWeather();
    }

    function setupEventListeners() {
        // Live Geolocation Button
        geoLocateBtn.addEventListener('click', () => {
            fetchLiveLocationWeather();
        });

        // Retry Button
        retryBtn.addEventListener('click', () => {
            fetchLiveLocationWeather();
        });

        // Refresh Button
        refreshDataBtn.addEventListener('click', () => {
            if (rawWeatherData && rawWeatherData.location) {
                const lat = rawWeatherData.location.latitude;
                const lon = rawWeatherData.location.longitude;
                fetchWeatherByCoords(lat, lon);
            } else {
                fetchLiveLocationWeather();
            }
        });

        // Unit Toggle Buttons
        unitToggleBtns.forEach(btn => {
            btn.addEventListener('click', (e) => {
                const selectedUnit = e.currentTarget.dataset.unit;
                if (selectedUnit !== currentUnit) {
                    currentUnit = selectedUnit;
                    unitToggleBtns.forEach(b => b.classList.remove('active'));
                    e.currentTarget.classList.add('active');
                    if (rawWeatherData) renderWeatherData(rawWeatherData);
                }
            });
        });

        // Search Input & Autocomplete
        citySearchInput.addEventListener('input', (e) => {
            const query = e.target.value.trim();
            if (query.length > 0) {
                clearSearchBtn.classList.remove('hidden');
            } else {
                clearSearchBtn.classList.add('hidden');
                autocompleteDropdown.classList.add('hidden');
            }

            clearTimeout(debounceTimer);
            if (query.length >= 2) {
                debounceTimer = setTimeout(() => fetchAutocomplete(query), 300);
            } else {
                autocompleteDropdown.classList.add('hidden');
            }
        });

        citySearchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const query = citySearchInput.value.trim();
                if (query) {
                    autocompleteDropdown.classList.add('hidden');
                    fetchWeatherByCity(query);
                }
            }
        });

        clearSearchBtn.addEventListener('click', () => {
            citySearchInput.value = '';
            clearSearchBtn.classList.add('hidden');
            autocompleteDropdown.classList.add('hidden');
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.search-container')) {
                autocompleteDropdown.classList.add('hidden');
            }
        });

        // Quick City Chips
        cityChips.forEach(chip => {
            chip.addEventListener('click', () => {
                const cityName = chip.dataset.city;
                citySearchInput.value = cityName;
                clearSearchBtn.classList.remove('hidden');
                fetchWeatherByCity(cityName);
            });
        });
    }

    // Geolocation API Handler
    function fetchLiveLocationWeather() {
        showLoading();
        if ("geolocation" in navigator) {
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    const lat = position.coords.latitude;
                    const lon = position.coords.longitude;
                    fetchWeatherByCoords(lat, lon);
                },
                (error) => {
                    console.warn("Geolocation permission denied/failed. Falling back to default (New Delhi).", error);
                    // Default fallback location (New Delhi: 28.6139, 77.2090)
                    fetchWeatherByCoords(28.6139, 77.2090);
                },
                { timeout: 8000, enableHighAccuracy: true }
            );
        } else {
            fetchWeatherByCoords(28.6139, 77.2090);
        }
    }

    // Backend API Calls
    async function fetchWeatherByCoords(lat, lon) {
        showLoading();
        try {
            const response = await fetch(`/api/weather/live?lat=${lat}&lon=${lon}`);
            if (!response.ok) throw new Error("Failed to fetch weather data for coordinates");
            const data = await response.json();
            rawWeatherData = data;
            renderWeatherData(data);
        } catch (err) {
            showError(err.message || "Failed to load live weather.");
        }
    }

    async function fetchWeatherByCity(cityName) {
        showLoading();
        try {
            const response = await fetch(`/api/weather/search?city=${encodeURIComponent(cityName)}`);
            if (!response.ok) {
                if (response.status === 404 || response.status === 400) {
                    throw new Error(`Location "${cityName}" not found. Try another city.`);
                }
                throw new Error("Unable to search city weather.");
            }
            const data = await response.json();
            rawWeatherData = data;
            renderWeatherData(data);
        } catch (err) {
            showError(err.message);
        }
    }

    async function fetchAutocomplete(query) {
        try {
            const response = await fetch(`/api/weather/autocomplete?q=${encodeURIComponent(query)}`);
            if (!response.ok) return;
            const suggestions = await response.json();
            renderAutocomplete(suggestions);
        } catch (ignored) {}
    }

    // Autocomplete Render
    function renderAutocomplete(items) {
        if (!items || items.length === 0) {
            autocompleteDropdown.classList.add('hidden');
            return;
        }

        autocompleteDropdown.innerHTML = items.map(item => `
            <div class="autocomplete-item" data-lat="${item.latitude}" data-lon="${item.longitude}" data-name="${item.name}">
                <span class="city">${item.name}</span>
                <span class="country">${item.region ? item.region + ', ' : ''}${item.country}</span>
            </div>
        `).join('');

        autocompleteDropdown.classList.remove('hidden');

        autocompleteDropdown.querySelectorAll('.autocomplete-item').forEach(el => {
            el.addEventListener('click', () => {
                const name = el.dataset.name;
                const lat = parseFloat(el.dataset.lat);
                const lon = parseFloat(el.dataset.lon);
                citySearchInput.value = name;
                autocompleteDropdown.classList.add('hidden');
                fetchWeatherByCoords(lat, lon);
            });
        });
    }

    // Render All Weather Components
    function renderWeatherData(data) {
        hideStateOverlay();

        const loc = data.location;
        const cur = data.current;

        // 1. Location Banner
        cityNameEl.textContent = loc.name || 'Unknown Location';
        regionCountryEl.textContent = [loc.region, loc.country].filter(Boolean).join(', ') || '';
        latValEl.textContent = loc.latitude.toFixed(2);
        lonValEl.textContent = loc.longitude.toFixed(2);
        lastUpdatedTimeEl.textContent = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        // 2. Temperature Conversion
        const temp = currentUnit === 'C' ? cur.temperature : convertToF(cur.temperature);
        const feelsLike = currentUnit === 'C' ? cur.feelsLike : convertToF(cur.feelsLike);

        currentTempEl.textContent = Math.round(temp);
        feelsLikeTempEl.textContent = Math.round(feelsLike);
        displayUnitEl.textContent = `°${currentUnit}`;
        feelsUnitEls.forEach(el => el.textContent = currentUnit);

        // 3. Condition Icon & Theme
        conditionTextEl.textContent = cur.weatherCondition || 'Clear';
        heroWeatherIcon.className = `fa-solid fa-${getFontAwesomeIcon(cur.weatherIcon)} weather-hero-icon`;
        updateBodyTheme(cur.weatherIcon, cur.isDay);

        // 4. Core Feature: HUMIDITY PERCENTAGE
        const hum = cur.humidity;
        humidityValEl.textContent = hum;
        humidityBarEl.style.width = `${hum}%`;
        
        let humStatus = "Optimal";
        let humDesc = "Comfortable humidity level";
        if (hum < 30) {
            humStatus = "Dry";
            humDesc = "Low humidity, air is dry";
        } else if (hum > 70) {
            humStatus = "Humid";
            humDesc = "High moisture content in air";
        }
        humidityStatusBadge.textContent = humStatus;
        humiditySubtext.textContent = humDesc;

        // 5. Core Feature: RAIN PROBABILITY PERCENTAGE
        const rainProb = cur.rainProbability;
        rainProbValEl.textContent = rainProb;
        rainProbBarEl.style.width = `${rainProb}%`;

        let rainStatus = "No Rain";
        let rainDesc = "Clear skies, low precipitation chance";
        if (rainProb >= 70) {
            rainStatus = "High Risk";
            rainDesc = "Rain is very likely today";
        } else if (rainProb >= 40) {
            rainStatus = "Moderate Risk";
            rainDesc = "Possibility of rain showers";
        } else if (rainProb >= 15) {
            rainStatus = "Low Risk";
            rainDesc = "Slight chance of rain";
        }
        rainStatusBadge.textContent = rainStatus;
        rainProbSubtext.textContent = rainDesc;

        // 6. Wind Speed
        windSpeedValEl.textContent = Math.round(cur.windSpeed);

        // 7. Hourly Forecast List
        renderHourlyForecast(data.hourly);

        // 8. Daily Forecast List
        renderDailyForecast(data.daily);
    }

    function renderHourlyForecast(hourlyList) {
        if (!hourlyList || hourlyList.length === 0) {
            hourlyContainer.innerHTML = '<p class="metric-subtext">Hourly data unavailable</p>';
            return;
        }

        hourlyContainer.innerHTML = hourlyList.map(h => {
            const hTemp = currentUnit === 'C' ? Math.round(h.temperature) : Math.round(convertToF(h.temperature));
            const iconClass = getFontAwesomeIcon(h.weatherIcon);
            return `
                <div class="hourly-card">
                    <span class="hourly-time">${h.time}</span>
                    <i class="fa-solid fa-${iconClass} hourly-icon"></i>
                    <span class="hourly-temp">${hTemp}°</span>
                    <span class="hourly-rain" title="Rain probability"><i class="fa-solid fa-umbrella"></i> ${h.rainProbability}%</span>
                </div>
            `;
        }).join('');
    }

    function renderDailyForecast(dailyList) {
        if (!dailyList || dailyList.length === 0) {
            dailyContainer.innerHTML = '<p class="metric-subtext">Daily forecast unavailable</p>';
            return;
        }

        dailyContainer.innerHTML = dailyList.map(d => {
            const maxT = currentUnit === 'C' ? Math.round(d.tempMax) : Math.round(convertToF(d.tempMax));
            const minT = currentUnit === 'C' ? Math.round(d.tempMin) : Math.round(convertToF(d.tempMin));
            const iconClass = getFontAwesomeIcon(d.weatherIcon);

            return `
                <div class="daily-card">
                    <span class="daily-day">${d.dayName}</span>
                    <i class="fa-solid fa-${iconClass} daily-icon"></i>
                    <span class="daily-condition">${d.weatherCondition}</span>
                    <div class="daily-temp-range">
                        <span class="temp-max">${maxT}°</span>
                        <span class="temp-min">${minT}°</span>
                    </div>
                    <span class="daily-rain"><i class="fa-solid fa-umbrella"></i> ${d.maxRainProbability}%</span>
                </div>
            `;
        }).join('');
    }

    // Helper Functions
    function convertToF(celsius) {
        return (celsius * 9 / 5) + 32;
    }

    function getFontAwesomeIcon(iconType) {
        switch(iconType) {
            case 'sun': return 'sun';
            case 'moon': return 'moon';
            case 'cloud-sun': return 'cloud-sun';
            case 'cloud-moon': return 'cloud-moon';
            case 'cloud': return 'cloud';
            case 'smog': return 'smog';
            case 'cloud-rain': return 'cloud-rain';
            case 'cloud-showers-heavy': return 'cloud-showers-heavy';
            case 'snowflake': return 'snowflake';
            case 'cloud-bolt': return 'cloud-bolt';
            default: return 'sun';
        }
    }

    function updateBodyTheme(iconType, isDay) {
        document.body.className = '';
        if (iconType.includes('rain') || iconType.includes('showers')) {
            document.body.classList.add('theme-rainy');
        } else if (iconType.includes('snowflake')) {
            document.body.classList.add('theme-snowy');
        } else if (iconType.includes('bolt')) {
            document.body.classList.add('theme-thunderstorm');
        } else if (iconType.includes('cloud')) {
            document.body.classList.add('theme-cloudy');
        } else {
            document.body.classList.add('theme-clear');
        }
    }

    function showLoading() {
        loadingState.classList.remove('hidden');
        errorState.classList.add('hidden');
        weatherDashboard.classList.add('hidden');
    }

    function showError(msg) {
        loadingState.classList.add('hidden');
        weatherDashboard.classList.add('hidden');
        errorState.classList.remove('hidden');
        errorMessage.textContent = msg;
    }

    function hideStateOverlay() {
        loadingState.classList.add('hidden');
        errorState.classList.add('hidden');
        weatherDashboard.classList.remove('hidden');
    }
});
