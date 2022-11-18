package bookshelf.catalog

import bookshelf.util.refined._
import cats.Applicative
import cats.data.ValidatedNel
import cats.FlatMap
import cats.MonadThrow
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Concurrent
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.HttpRoutes
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.HttpVersion
import org.http4s.QueryParameterValue

class CatalogRoutes[F[_]: Concurrent: MonadThrow] extends Http4sDsl[F] {

  // add, get, update (including archive) books
  // get all by genre (paginated, should I use stream and leave the connection open?)
  // get all by author
  // full text search on title/genre/summary

  def genreRoutes(genres: Genres[F]): HttpRoutes[F] = {
    import Genres._
    // import Genres.EqualMe._

    object GenreQueryParamMatcher extends NamedValidatingQueryParamDecoderMatcher[GenreName]("name")

    HttpRoutes.of {
      case GET -> Root / "all" => Ok(genres.getAll)

      case GET -> Root :? GenreQueryParamMatcher(maybeGenre) =>
        // TODO: look-up doc: there must be a generic way of returning this error
        maybeGenre match {
          case Valid(genre) => Ok(genres.get(genre))
          case Invalid(e) =>
            val message = e.map(_.sanitized).mkString_(",")
            BadRequest(message)
        }

      // TODO: this action should require authentication
      case req @ POST -> Root => {
        for {
          genre <- req.as[Genre]
          key <- genres.add(genre)
          response <- Ok(key)
        } yield response
      }
    }
  }

  def authorRoutes(authors: Authors[F]): HttpRoutes[F] = {
    HttpRoutes.of { case GET -> Root / "all" =>
      authors.getAll.flatMap(Ok(_))
    }
  }

  // book routes here

  def routes(genres: Genres[F], authors: Authors[F]): HttpRoutes[F] =
    Router(
      "genre" -> genreRoutes(genres),
      "author" -> authorRoutes(authors)
    )

}
