package weatherapp.frontend

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object Main {

  // ── Model ────────────────────────────────────────────────────────

  final case class City(name: String, lat: Double, lon: Double)

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

  // Ignore responses from requests that were superseded by a newer one.
  private var requestId = 0

  // ── Backend calls ────────────────────────────────────────────────

  private def load(lat: Double, lon: Double): Unit = {
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
      (pos: dom.Position) => load(pos.coords.latitude, pos.coords.longitude),
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
      div(cls := "card muted", "Pick a city or enter coordinates.")
    case Loading =>
      div(cls := "card muted", "Loading forecast…")
    case Failed(msg) =>
      div(cls := "card error", msg)
    case Loaded(f) =>
      div(
        cls := "card",
        div(cls := "temp", s"${f.temperature}°${f.temperatureUnit}"),
        div(cls := "desc", f.shortForecast),
        span(cls := s"badge ${f.characterization}", f.characterization)
      )
  }

  private val app: HtmlElement =
    div(
      cls := "container",
      h1("Weather"),
      p(cls := "subtitle", "Today's forecast from the US National Weather Service (US locations only)."),
      div(
        cls := "cities",
        cities.map { c =>
          button(cls := "chip", c.name, disabled <-- isLoading, onClick --> (_ => load(c.lat, c.lon)))
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
