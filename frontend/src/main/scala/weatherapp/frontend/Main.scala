package weatherapp.frontend

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object Main {

  // ── Model ────────────────────────────────────────────────────────

  final case class City(name: String, lat: Double, lon: Double)

  final case class Place(name: String, state: Option[String], county: Option[String], lat: Double, lon: Double) {
    def label: String = (name :: state.toList).mkString(", ")
  }

  sealed trait SearchState
  case object SearchIdle                          extends SearchState
  case object Searching                           extends SearchState
  final case class Suggestions(ps: List[Place])   extends SearchState
  final case class SearchFailed(msg: String)      extends SearchState

  final case class Forecast(
      shortForecast: String,
      temperature: Int,
      temperatureUnit: String,
      characterization: String
  )

  sealed trait State
  case object Idle                       extends State
  case object Loading                    extends State
  final case class Loaded(f: Forecast)   extends State
  final case class Failed(msg: String)   extends State

  private val cities = List(
    City("Kansas", 39.7456, -97.0892),
    City("New York", 40.7128, -74.0060),
    City("Miami", 25.7617, -80.1918),
    City("Charlotte", 35.2271, -80.8431),
    City("San Francisco", 37.7749, -122.4194)
  )

  private val state  = Var[State](Idle)
  private val latVar = Var("")
  private val lonVar = Var("")

  private val searchVar   = Var("")
  private val searchState = Var[SearchState](SearchIdle)
  private val placeLabel  = Var[Option[String]](None)
  private var searchId    = 0

  // Ignore responses from requests that were superseded by a newer one.
  private var requestId = 0

  // ── Backend calls ────────────────────────────────────────────────

  private def load(lat: Double, lon: Double, label: Option[String] = None): Unit = {
    placeLabel.set(label)
    latVar.set(lat.toString)
    lonVar.set(lon.toString)
    state.set(Loading)
    requestId += 1
    val id = requestId

    dom
      .fetch(s"/forecast?lat=$lat&lon=$lon")
      .toFuture
      .flatMap(r => r.text().toFuture.map(body => (r.ok, body)))
      .onComplete { result =>
        if (id == requestId) state.set(result match {
          case Success((true, body))  => parseForecast(body)
          case Success((false, body)) => Failed(parseError(body))
          case Failure(e)             => Failed(s"Could not reach the server: ${e.getMessage}")
        })
      }
  }

  private def parseForecast(body: String): State =
    Try {
      val j = js.JSON.parse(body)
      Forecast(
        shortForecast = j.shortForecast.asInstanceOf[String],
        temperature = j.temperature.asInstanceOf[Double].toInt,
        temperatureUnit = j.temperatureUnit.asInstanceOf[String],
        characterization = j.characterization.asInstanceOf[String]
      )
    }.fold(_ => Failed("Unexpected response from the server."), Loaded(_))

  /** Looks up places by name; if `autoPick` and there's exactly one match, loads it right away. */
  private def searchPlaces(query: String, autoPick: Boolean = false): Unit = {
    val q = query.trim
    searchId += 1
    val id = searchId
    if (q.length < 2) searchState.set(SearchIdle)
    else {
      searchState.set(Searching)
      dom
        .fetch(s"/geocode?q=${js.URIUtils.encodeURIComponent(q)}")
        .toFuture
        .flatMap(r => r.text().toFuture.map(body => (r.ok, body)))
        .onComplete { result =>
          if (id == searchId) result match {
            case Success((true, body)) =>
              parsePlaces(body) match {
                case Some(p :: Nil) if autoPick => pickPlace(p)
                case Some(ps)                   => searchState.set(Suggestions(ps))
                case None                       => searchState.set(SearchFailed("Unexpected response from the server."))
              }
            case Success((false, body)) => searchState.set(SearchFailed(parseError(body)))
            case Failure(e)             => searchState.set(SearchFailed(s"Could not reach the server: ${e.getMessage}"))
          }
        }
    }
  }

  private def parsePlaces(body: String): Option[List[Place]] =
    Try {
      js.JSON.parse(body).asInstanceOf[js.Array[js.Dynamic]].toList.map { j =>
        def opt(v: js.Dynamic) = if (js.isUndefined(v) || v == null) None else Some(v.asInstanceOf[String])
        Place(
          name = j.name.asInstanceOf[String],
          state = opt(j.state),
          county = opt(j.county),
          lat = j.lat.asInstanceOf[Double],
          lon = j.lon.asInstanceOf[Double]
        )
      }
    }.toOption

  private def pickPlace(p: Place): Unit = {
    searchId += 1 // drop any in-flight search
    searchVar.set(p.label)
    searchState.set(SearchIdle)
    load(p.lat, p.lon, Some(p.label))
  }

  private def parseError(body: String): String =
    Try(js.JSON.parse(body).error.asInstanceOf[String])
      .toOption
      .filter(_ != null)
      .getOrElse("Request failed.")

  private def submitCoords(): Unit =
    (latVar.now().trim.toDoubleOption, lonVar.now().trim.toDoubleOption) match {
      case (Some(lat), Some(lon)) if lat.abs <= 90 && lon.abs <= 180 => load(lat, lon)
      case _ => state.set(Failed("Enter a latitude (-90 to 90) and longitude (-180 to 180)."))
    }

  private def useMyLocation(): Unit = {
    state.set(Loading)
    dom.window.navigator.geolocation.getCurrentPosition(
      (pos: dom.Position) => load(pos.coords.latitude, pos.coords.longitude, Some("Your location")),
      (err: dom.PositionError) => state.set(Failed(s"Location unavailable: ${err.message}"))
    )
  }

  // ── View ─────────────────────────────────────────────────────────

  private val isLoading: Signal[Boolean] = state.signal.map(_ == Loading)

  private def coordInput(lbl: String, v: Var[String], hint: String) =
    label(
      span(lbl),
      input(
        typ := "text",
        placeholder := hint,
        controlled(value <-- v, onInput.mapToValue --> v)
      )
    )

  private def renderState(s: State): HtmlElement = s match {
    case Idle =>
      div(cls := "card muted", "Search for a place, pick a city, or enter coordinates.")
    case Loading =>
      div(cls := "card muted", "Loading forecast…")
    case Failed(msg) =>
      div(cls := "card error", msg)
    case Loaded(f) =>
      div(
        cls := "card",
        child.maybe <-- placeLabel.signal.map(_.map(l => div(cls := "place", l))),
        div(cls := "temp", s"${f.temperature}°${f.temperatureUnit}"),
        div(cls := "desc", f.shortForecast),
        span(cls := s"badge ${f.characterization}", f.characterization)
      )
  }

  private def renderSearchState(s: SearchState): Option[HtmlElement] = s match {
    case SearchIdle          => None
    case Searching           => Some(div(cls := "dropdown muted", "Searching…"))
    case SearchFailed(msg)   => Some(div(cls := "dropdown error", msg))
    case Suggestions(Nil)    => Some(div(cls := "dropdown muted", "No US places found. Try adding a state, e.g. \"Springfield, IL\"."))
    case Suggestions(places) =>
      Some(
        ul(
          cls := "dropdown",
          places.map { p =>
            li(
              button(
                typ := "button",
                cls := "option",
                span(cls := "option-name", p.name),
                span(cls := "option-meta", (p.county.map(c => if (c.endsWith(" County")) c else s"$c County").toList ++ p.state.toList).mkString(", ")),
                onClick --> (_ => pickPlace(p))
              )
            )
          }
        )
      )
  }

  private val searchBox: HtmlElement =
    form(
      cls := "search",
      onSubmit.preventDefault --> (_ => searchPlaces(searchVar.now(), autoPick = true)),
      div(
        cls := "search-row",
        input(
          typ := "search",
          placeholder := "City, town or village (optionally \", State\")",
          aria.label := "Search for a place",
          autoComplete := "off",
          controlled(value <-- searchVar, onInput.mapToValue --> searchVar),
          // Type-ahead: search once the user pauses typing.
          onInput.mapToValue.compose(_.debounce(300)) --> (q => searchPlaces(q)),
          onKeyDown.filter(_.key == "Escape") --> (_ => searchState.set(SearchIdle))
        ),
        button(typ := "submit", "Search")
      ),
      child.maybe <-- searchState.signal.map(renderSearchState)
    )

  private val app: HtmlElement =
    div(
      cls := "container",
      h1("Weather"),
      p(cls := "subtitle", "Today's forecast from the US National Weather Service (US locations only)."),
      searchBox,
      div(
        cls := "cities",
        cities.map { c =>
          button(cls := "chip", c.name, disabled <-- isLoading, onClick --> (_ => load(c.lat, c.lon, Some(c.name))))
        }
      ),
      form(
        cls := "coords",
        onSubmit.preventDefault --> (_ => submitCoords()),
        coordInput("Latitude", latVar, "40.7128"),
        coordInput("Longitude", lonVar, "-74.0060"),
        div(
          cls := "actions",
          button(typ := "submit", "Get forecast", disabled <-- isLoading),
          button(
            typ := "button",
            cls := "secondary",
            "Use my location",
            disabled <-- isLoading,
            onClick --> (_ => useMyLocation())
          )
        )
      ),
      child <-- state.signal.map(renderState)
    )

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
}
