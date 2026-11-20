package weatherapp

import cats.effect.Async
import cats.implicits._
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.staticcontent.resourceServiceBuilder

/** Serves the Scala.js UI: index.html at `/`, plus main.js etc. from classpath `/public`. */
object StaticRoutes {

  def routes[F[_]: Async]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    val index = HttpRoutes.of[F] { case req @ GET -> Root =>
      StaticFile.fromResource[F]("/public/index.html", Some(req)).getOrElseF(NotFound())
    }

    index <+> resourceServiceBuilder[F]("/public").toRoutes
  }
}
