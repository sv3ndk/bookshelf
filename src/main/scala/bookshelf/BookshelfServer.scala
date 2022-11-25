package bookshelf

import bookshelf.catalog.Books
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.catalog._
import bookshelf.utils.validation._
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import doobie.hikari._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
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

object BookshelfServer {

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](2)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql:bookshelf",
        "testuser",
        "testpassword",
        ce
      )
    } yield xa

  def bookshelfApp(xa: Transactor[IO]): HttpApp[IO] = {
    val httpApp = Router
      .of(
        "catalog" -> CatalogRoutes.routes(
          Categories.make(xa),
          Authors.make(xa),
          Books.make(xa)
        )
      )
      .orNotFound

    // middleware
    val finalHttpApp = Logger.httpApp(true, true)(httpApp)
    finalHttpApp
  }

  def server: IO[Nothing] =
    transactor
      .map(xa => bookshelfApp(xa))
      .flatMap(app =>
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(ErrorHandling.Recover.messageFailure(app))
          .build
      )
      .use(_ => IO.never[Nothing])
}

object BookshelfServerApp extends IOApp {
  def run(args: List[String]) =
    BookshelfServer.server.as(ExitCode.Success)
}
