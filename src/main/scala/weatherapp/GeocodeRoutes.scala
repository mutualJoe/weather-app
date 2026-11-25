package weatherapp

import cats.effect.Concurrent
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.circe.CirceEntityEncoder._

object GeocodeRoutes {

  private object QueryParam extends QueryParamDecoderMatcher[String]("q")

  def routes[F[_]: Concurrent](service: GeocodingService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] { case GET -> Root / "geocode" :? QueryParam(q) =>
      service
        .search(q)
        .flatMap(Ok(_))
        .handleErrorWith { err =>
          BadGateway(io.circe.Json.obj("error" -> io.circe.Json.fromString(s"Geocoding failed: ${err.getMessage}")))
        }
    }
  }
}
