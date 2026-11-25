### Weather service (working)

#### This is a small service that calls https://api.weather.gov and make simple forecasts.

#### Usage:

```
sbt run
```

#### Web UI

Open http://localhost:8080 in a browser. Search for a city/town/village by name (suggestions
appear as you type; add a state to narrow it down, e.g. `Springfield, IL`), pick a preset city,
type a latitude/longitude, or use "Use my location" (US locations only, since the data comes from the NWS).

The UI is written in Scala.js with Laminar (`frontend/`). `sbt run` compiles it and the
server serves it automatically; no separate build step is needed.

#### API

Or in the terminal 
```
# Kansas (random US center point)
curl "http://localhost:8080/forecast?lat=39.7456&lon=-97.0892"

# New York City
curl "http://localhost:8080/forecast?lat=40.7128&lon=-74.0060"

# Miami
curl "http://localhost:8080/forecast?lat=25.7617&lon=-80.1918"

# San Francisco
curl "http://localhost:8080/forecast?lat=37.7749&lon=-122.4194"
```
or just make GET requests to these urls

To look up coordinates by place name (uses the free [Open-Meteo geocoding API](https://open-meteo.com/en/docs/geocoding-api), no key needed):
```
curl "http://localhost:8080/geocode?q=Springfield"       # all US Springfields
curl "http://localhost:8080/geocode?q=Springfield,%20IL" # narrowed to Illinois
```
returns a list of `{"name", "state", "county", "lat", "lon"}`.

So first we go to google.com and put requests like `san francisco lat and long`

once lat and long are obtained we can make the calls above and get results in this format

```
{
  "shortForecast": "Chance Showers And Thunderstorms",
  "temperature": 62,
  "temperatureUnit": "F",
  "characterization": "moderate"
}
or
{
  "shortForecast": "Areas Of Fog then Mostly Cloudy",
  "temperature": 67,
  "temperatureUnit": "F",
  "characterization": "moderate"
}
etc
```
to make the text formatted jq tool can be used
(on mac)
```
brew install jq 
```
and then
```
curl "http://localhost:8080/forecast?lat=39.7456&lon=-97.0892" | jq
```

### Tech stack

Language & Runtime: `Scala 2.13` on the JVM, built with `sbt`

Effect System: `Cats Effect 3` — provides runtime, with tagless final approach (F[_])

HTTP: `http4s 0.23` — HTTP library

`Ember server` (non-blocking, built on `fs2`)
`Ember client` (for outbound calls to the NWS API)
JSON: `Circe` — automatic json codec 

Frontend: `Scala.js` + `Laminar` — compiled to `main.js` and served by the http4s server

Testing: `MUnit` with `munit-cats-effect` for IO-based assertions


