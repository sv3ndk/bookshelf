package bookshelf

import cats.effect.{Async, Resource}
import cats.syntax.all._
// import cats.syntax.applicative._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.HttpApp
import org.http4s.server.Router
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Genres
import bookshelf.catalog.Authors

object BookshelfServer {

  def bikeApp[F[_]: Async]: Resource[F, HttpApp[F]] = for {
    client <- EmberClientBuilder.default[F].build
    // jokeAlg = Jokes.impl[F](client
    genres <- Resource.eval(Genres.make[F])
    authors <- Resource.eval(Authors.make[F])

    httpApp = Router
      .of(
        // ("joke", BikeconfiguratorRoutes.jokeRoutes[F](jokeAlg)),
        "catalog" -> new CatalogRoutes[F].routes(genres, authors)
      )
      .orNotFound

    // With Middlewares in place
    finalHttpApp = Logger.httpApp(true, true)(httpApp)
  } yield finalHttpApp

  def server[F[_]: Async]: Stream[F, Nothing] = {
    for {
      theApp <- Stream.resource(bikeApp)

      exitCode <- Stream.resource(
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(theApp)
          .build >>
          Resource.eval(Async[F].never)
      )
    } yield exitCode
  }.drain
}
