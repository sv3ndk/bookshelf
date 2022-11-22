package bookshelf

import bookshelf.catalog.Authors
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.validation._
import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.EntityEncoder
import org.http4s.HttpApp
import org.http4s.HttpVersion
import org.http4s.InvalidBodyException
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MessageFailure
import org.http4s.ParseFailure
import org.http4s.Response
import org.http4s.Status
import org.http4s.client.UnexpectedStatus
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.ErrorHandling
import org.http4s.server.middleware.Logger
import bookshelf.catalog.Books

object BookshelfServer {

  def bookshelfApp[F[_]: Async]: Resource[F, HttpApp[F]] = for {
    client <- EmberClientBuilder.default[F].build
    // jokeAlg = Jokes.impl[F](client
    genres <- Resource.eval(Categories.make[F])
    authors <- Resource.eval(Authors.make[F])
    books <- Resource.eval(Books.make[F])

    httpApp = Router
      .of(
        // ("joke", BikeconfiguratorRoutes.jokeRoutes[F](jokeAlg)),
        "catalog" -> new CatalogRoutes[F].routes(genres, authors, books)
      )
      .orNotFound

    // With Middlewares in place
    finalHttpApp = Logger.httpApp(true, true)(httpApp)
  } yield finalHttpApp

  def server[F[_]: Async]: Stream[F, Nothing] = {
    for {
      theApp <- Stream.resource(bookshelfApp)

      exitCode <- Stream.resource(
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(ErrorHandling.Recover.messageFailure(theApp))
          .build >>
          Resource.eval(Async[F].never)
      )
    } yield exitCode
  }.drain
}
