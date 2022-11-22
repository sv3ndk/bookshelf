package bookshelf.catalog

import bookshelf.utils.validation.CommonErrorMessages._
import bookshelf.utils.validation._
import cats.Applicative
import cats.FlatMap
import cats.MonadError
import cats.MonadThrow
import cats.data.NonEmptyList
import cats.data.Validated
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

import CatalogRoutes._

class CatalogRoutes[F[_]: Concurrent: MonadThrow] extends Http4sDsl[F] {

  // add, get, update (including archive) books
  // get all by genre (paginated, should I use stream and leave the connection open?)
  // get all by author
  // full text search on title/genre/summary

  def categoryRoutes(categories: Categories[F]): HttpRoutes[F] = {

    import Categories._

    object NameQueryParamMatcher extends NamedQueryParamDecoderMatcher[CategoryName]("name")

    HttpRoutes.of {
      case GET -> Root / "all" => Ok(categories.getAll)

      // TODO: we should get a category by id instead
      case GET -> Root :? NameQueryParamMatcher(catParam) =>
        validated(catParam)
          .flatMap(category => categories.get(category))
          .flatMap(Ok(_))

      // TODO: this action should require authentication
      case req @ POST -> Root =>
        req
          .as[RawCategory]
          .flatMap(raw => validated(raw.asDomain))
          .flatMap(cat => categories.add(cat))
          .flatMap(Ok(_))
    }
  }

  def authorRoutes(authors: Authors[F]): HttpRoutes[F] = {
    HttpRoutes.of { case GET -> Root / "all" =>
      authors.getAll
        .flatMap(Ok(_))
    }
  }

  def bookRoutes(books: Books[F]): HttpRoutes[F] = {

    object IdQueryParamMatcher extends NamedQueryParamDecoderMatcher[Books.BookId]("id")

    HttpRoutes.of {

      case GET -> Root :? IdQueryParamMatcher(idParam) =>
        validated(idParam)
          .flatMap(id => books.get(id))
          .flatMap(Ok(_))

      case req @ POST -> Root =>
        req
          .as[RawBook]
          .flatMap(raw => validated(raw.asDomain))
          .flatMap(book => books.add(book))
          .flatMap(Ok(_))
    }
  }

  def routes(genres: Categories[F], authors: Authors[F], books: Books[F]): HttpRoutes[F] =
    Router(
      "category" -> categoryRoutes(genres),
      "author" -> authorRoutes(authors),
      "book" -> bookRoutes(books)
    )
}

object CatalogRoutes {
  import Categories._
  case class RawCategory(id: String, name: String, description: String) {
    def asDomain: ValidatedNel[ParseFailure, Category] = (
      refineVDetailed[CategoryId](id, "id").toValidatedNel,
      refineVDetailed[CategoryName](name, "name").toValidatedNel,
      refineVDetailed[CategoryDescription](description, "description").toValidatedNel
    ).mapN(Category)
  }

  import Books._
  case class RawBook(
      id: String,
      title: String,
      authorId: String,
      publicationYear: Int,
      categories: List[String],
      summary: String
  ) {
    def asDomain: ValidatedNel[ParseFailure, Books.Book] = (
      refineVDetailed[Books.BookId](id, "id").toValidatedNel,
      refineVDetailed[Books.BookTitle](title, "title").toValidatedNel,
      refineVDetailed[Authors.AuthorId](authorId, "authorId").toValidatedNel,
      refineVDetailed[Books.BookPublicationYear](publicationYear, "publicationYear").toValidatedNel,
      categories.traverse(category => refineVDetailed[CategoryId](category, "categories").toValidatedNel),
      Validated.validNel[ParseFailure, String](summary)
    ).mapN(Book)
  }

}
