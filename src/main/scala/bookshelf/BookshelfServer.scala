package bookshelf

import bookshelf.catalog.Books
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.authentication.authMiddleware
import bookshelf.catalog._
import bookshelf.utils.validation._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import doobie.hikari._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.http4s.EntityEncoder
import org.http4s.HttpApp
import org.http4s.InvalidBodyException
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Response
import org.http4s.Status
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.ErrorHandling
import org.http4s.server.middleware.Logger
import org.log4s._

case class AppConfig(
    rdbmsHost: String,
    rdbmsPort: Int
)

object BookshelfServer {

  def localHostTransactor(config: AppConfig): Resource[IO, HikariTransactor[IO]] = {

    val connectionString = s"jdbc:postgresql://${config.rdbmsHost}:${config.rdbmsPort}/bookshelf"

    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](2)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        connectionString,
        "testuser",
        "testpassword",
        ce
      )
    } yield xa
  }

  def bookshelfApp(xa: Transactor[IO]): HttpApp[IO] = {
    val httpApp = Router
      .of(
        "catalog" -> CatalogRoutes.routes(authMiddleware, Categories(xa), Authors(xa), Books(xa))
      )
      .orNotFound

    // middlewares
    ErrorHandling.Recover.total(
      Logger.httpApp(true, true)(httpApp)
    )
  }

  def localBookshelfApp(config: AppConfig): Resource[IO, HttpApp[IO]] =
    localHostTransactor(config).map(bookshelfApp)

  def server(config: AppConfig): IO[Nothing] =
    localBookshelfApp(config)
      .flatMap(app =>
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(app)
          .build
      )
      .use(_ => IO.never[Nothing])
}

object BookshelfServerApp extends IOApp {

  private val logger = getLogger

  val localDockerConfig = AppConfig(
    rdbmsHost = "localhost",
    rdbmsPort = 5432
  )
  def run(args: List[String]) = {
    logger.info(s"starting application with config $localDockerConfig")
    BookshelfServer.server(localDockerConfig).as(ExitCode.Success)
  }
}
