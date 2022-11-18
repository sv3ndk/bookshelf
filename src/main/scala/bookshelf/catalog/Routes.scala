package bookshelf.catalog

import bookshelf.util.validation._
import cats.Applicative
import cats.FlatMap
import cats.MonadError
import cats.MonadThrow
import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.Concurrent
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.HttpRoutes
import org.http4s.HttpVersion
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MalformedMessageBodyFailure
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.QueryParameterValue
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

import Genres._

class CatalogRoutes[F[_]: Concurrent: MonadThrow] extends Http4sDsl[F] {
  import CatalogRoutes._

  // add, get, update (including archive) books
  // get all by genre (paginated, should I use stream and leave the connection open?)
  // get all by author
  // full text search on title/genre/summary

  def genreRoutes(genres: Genres[F]): HttpRoutes[F] = {

    object GenreQueryParamMatcher extends NamedValidatingQueryParamDecoderMatcher[GenreName]("name")

    HttpRoutes.of {
      case GET -> Root / "all" => Ok(genres.getAll)

      case GET -> Root :? GenreQueryParamMatcher(maybeGenre) =>
        validParam(maybeGenre).flatMap(genre => Ok(genres.get(genre)))

      // TODO: this action should require authentication
      case req @ POST -> Root =>
        for {
          wGenre <- req.as[WGenre]
          genre <- validBody(wGenre.asDomain)
          response <- genres.add(genre).flatMap(Ok(_))
        } yield response
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

object CatalogRoutes {
  case class WGenre(name: String, description: String) {
    def asDomain: ValidatedNel[DetailedValidationErr, Genre] = (
      refineVDetailed[String, GenreNameConstraint](name, "genreName").toValidatedNel,
      refineVDetailed[String, GenreDescriptionConstraint](description, "description").toValidatedNel
    ).mapN(Genre)
  }

}
