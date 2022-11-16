package bookshelf.catalog

import cats.Applicative
import cats.FlatMap
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.syntax.all._
import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router

// will become a class when we can inject the business logic
object CatalogRoutes {

  // add, get, update (including archive) books
  // get all by genre (paginated, should I use stream and leave the connection open?)
  // get all by author
  // full text search on title/genre/summary

  def genreRoutes[F[_]: Concurrent](genres: Genres[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of {
      case GET -> Root / "all" =>
        genres.getAll.flatMap(Ok(_))

      case GET -> Root / genre =>
        genres.get(genre).flatMap(Ok(_))

      case req @ POST -> Root / genre =>
        for {
          genre <- req.as[Genre]
          key <- genres.add(genre)
          response <- Ok(key)
        } yield response
    }
  }

  def authorRoutes[F[_]: Concurrent](authors: Authors[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of { case GET -> Root / "all" =>
      authors.getAll.flatMap(Ok(_))
    }
  }

  // book routes here

  def routes[F[_]: Concurrent](genres: Genres[F], authors: Authors[F]): HttpRoutes[F] =
    Router(
      ("genre", genreRoutes(genres)),
      ("author", authorRoutes(authors))
    )

}
