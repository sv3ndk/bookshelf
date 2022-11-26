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
import cats.effect.IO
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.util.transactor
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

object CatalogRoutes extends Http4sDsl[IO] {
  import Categories._
  import Authors._
  import Books._

  def categoryRoutes(categories: Categories): HttpRoutes[IO] = {
    object NameQueryParamMatcher extends NamedQueryParamDecoderMatcher[CategoryName]("name")

    HttpRoutes.of {
      case GET -> Root :? NameQueryParamMatcher(catParam) =>
        IO.fromTry(validated(catParam))
          .flatMap(category => categories.get(category))
          .flatMap(Ok(_))

      case GET -> Root / "all" => categories.getAll.flatMap(Ok(_))

      case req @ POST -> Root =>
        req
          .as[RawCreateCategory]
          .flatMap(raw => IO.fromTry(validated(raw.asDomain)))
          .flatMap(categories.create)
          .flatMap(Created(_))
    }
  }

  def authorRoutes(authors: Authors): HttpRoutes[IO] = {
    object IdQueryParamMatcher extends NamedQueryParamDecoderMatcher[AuthorId]("id")

    HttpRoutes.of {
      case GET -> Root :? IdQueryParamMatcher(idParam) =>
        IO.fromTry(validated(idParam))
          .flatMap(id => authors.get(id))
          .flatMap(Ok(_))

      case GET -> Root / "all" => authors.getAll.flatMap(Ok(_))

      case req @ POST -> Root =>
        req
          .as[RawCreateAuthor]
          .flatMap(raw => IO.fromTry(validated(raw.asDomain)))
          .flatMap(authors.create)
          .flatMap(Created(_))

    }
  }

  def bookRoutes(books: Books): HttpRoutes[IO] = {
    object IdQueryParamMatcher extends NamedQueryParamDecoderMatcher[Books.BookId]("id")

    HttpRoutes.of {
      case GET -> Root :? IdQueryParamMatcher(idParam) =>
        IO.fromTry(validated(idParam))
          .flatMap(id => books.get(id))
          .flatMap(Ok(_))

      case req @ POST -> Root =>
        req
          .as[RawCreateBook]
          .flatMap(raw => IO.fromTry(validated(raw.asDomain)))
          .flatMap(books.create)
          .flatMap(Created(_))
    }
  }

  def routes(categories: Categories, authors: Authors, books: Books): HttpRoutes[IO] =
    Router(
      "category" -> categoryRoutes(categories),
      "author" -> authorRoutes(authors),
      "book" -> bookRoutes(books)
    )

  case class RawCreateCategory(name: String, description: String) {
    def asDomain: ValidatedNel[ParseFailure, CreateCategory] = (
      refineVDetailed[CategoryName](name, "name").toValidatedNel,
      refineVDetailed[CategoryDescription](description, "description").toValidatedNel
    ).mapN(CreateCategory)
  }

  case class RawCreateAuthor(firstName: String, lastName: String) {
    def asDomain: ValidatedNel[ParseFailure, CreateAuthor] = (
      refineVDetailed[Authors.FirstName](firstName, "firstName").toValidatedNel,
      refineVDetailed[Authors.LastName](lastName, "lastName").toValidatedNel
    ).mapN(CreateAuthor)
  }

  case class RawCreateBook(
      title: String,
      authorId: String,
      publicationYear: Int,
      categoryIds: List[String],
      summary: String
  ) {
    def asDomain: ValidatedNel[ParseFailure, CreateBook] = (
      refineVDetailed[Books.BookTitle](title, "title").toValidatedNel,
      refineVDetailed[Authors.AuthorId](authorId, "authorId").toValidatedNel,
      refineVDetailed[Books.BookPublicationYear](publicationYear, "publicationYear").toValidatedNel,
      categoryIds.traverse(catId => refineVDetailed[Categories.CategoryId](catId, "categoryIds").toValidatedNel),
      Validated.validNel[ParseFailure, String](summary)
    ).mapN(CreateBook)
  }

}
