package weatherapp

import cats.effect.Concurrent
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.implicits._

// ── Open-Meteo geocoding response shapes ───────────────────────────

final case class GeoResult(
    name: String,
    latitude: Double,
    longitude: Double,
    admin1: Option[String], // state
    admin2: Option[String], // county
    population: Option[Long]
)
object GeoResult {
  implicit val decoder: Decoder[GeoResult] = deriveDecoder
}

final case class GeoResponse(results: Option[List[GeoResult]])
object GeoResponse {
  implicit val decoder: Decoder[GeoResponse] = deriveDecoder
}

// ── API response ───────────────────────────────────────────────

final case class Place(name: String, state: Option[String], county: Option[String], lat: Double, lon: Double)
object Place {
  implicit val encoder: Encoder[Place] = deriveEncoder
}

trait GeocodingService[F[_]] {

  /** Looks up US places by name. Accepts "Springfield" or "Springfield, IL" / "Springfield, Illinois". */
  def search(query: String): F[List[Place]]
}

object GeocodingService {

  private val MaxResults = 10

  def impl[F[_]: Concurrent](client: Client[F]): GeocodingService[F] =
    new GeocodingService[F] {
      override def search(query: String): F[List[Place]] = {
        val (name, stateFilter) = parseQuery(query)
        if (name.length < 2) List.empty[Place].pure[F]
        else {
          // Fetch extra results when filtering by state, so the filter has something to work with.
          val count = if (stateFilter.isDefined) 100 else MaxResults
          val uri = uri"https://geocoding-api.open-meteo.com/v1/search".withQueryParams(
            Map("name" -> name, "count" -> count.toString, "language" -> "en", "format" -> "json", "countryCode" -> "US")
          )
          client.expect[GeoResponse](uri).map { resp =>
            resp.results
              .getOrElse(Nil)
              .filter(r => stateFilter.forall(s => r.admin1.exists(_.equalsIgnoreCase(s))))
              .take(MaxResults)
              .map(r => Place(r.name, r.admin1, r.admin2, r.latitude, r.longitude))
          }
        }
      }
    }

  /** Splits "Name, State" into the name and an optional full state name (abbreviations expanded). */
  def parseQuery(query: String): (String, Option[String]) =
    query.split(",", 2).map(_.trim) match {
      case Array(name, state) if state.nonEmpty =>
        (name, Some(StateCodes.getOrElse(state.toUpperCase, state)))
      case parts => (parts.head, None)
    }

  private val StateCodes: Map[String, String] = Map(
    "AL" -> "Alabama", "AK" -> "Alaska", "AZ" -> "Arizona", "AR" -> "Arkansas", "CA" -> "California",
    "CO" -> "Colorado", "CT" -> "Connecticut", "DE" -> "Delaware", "DC" -> "District of Columbia",
    "FL" -> "Florida", "GA" -> "Georgia", "HI" -> "Hawaii", "ID" -> "Idaho", "IL" -> "Illinois",
    "IN" -> "Indiana", "IA" -> "Iowa", "KS" -> "Kansas", "KY" -> "Kentucky", "LA" -> "Louisiana",
    "ME" -> "Maine", "MD" -> "Maryland", "MA" -> "Massachusetts", "MI" -> "Michigan", "MN" -> "Minnesota",
    "MS" -> "Mississippi", "MO" -> "Missouri", "MT" -> "Montana", "NE" -> "Nebraska", "NV" -> "Nevada",
    "NH" -> "New Hampshire", "NJ" -> "New Jersey", "NM" -> "New Mexico", "NY" -> "New York",
    "NC" -> "North Carolina", "ND" -> "North Dakota", "OH" -> "Ohio", "OK" -> "Oklahoma", "OR" -> "Oregon",
    "PA" -> "Pennsylvania", "RI" -> "Rhode Island", "SC" -> "South Carolina", "SD" -> "South Dakota",
    "TN" -> "Tennessee", "TX" -> "Texas", "UT" -> "Utah", "VT" -> "Vermont", "VA" -> "Virginia",
    "WA" -> "Washington", "WV" -> "West Virginia", "WI" -> "Wisconsin", "WY" -> "Wyoming", "PR" -> "Puerto Rico"
  )
}
