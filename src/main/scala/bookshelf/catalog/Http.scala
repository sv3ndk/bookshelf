package bookshelf.catalog

import bookshelf.utils.validation.CommonErrorMessages._
import bookshelf.utils.validation._
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import cats.syntax.all._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uuid
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.HttpRoutes
import org.http4s.ParseFailure
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.log4s._
import org.http4s.Status

object CatalogRoutes extends Http4sDsl[IO] {
  import Categories._
  import Authors._
  import Books._

  def categoryRoutes(categories: Categories): HttpRoutes[IO] = {
    val nameQueryParamMatcher = namedQueryParamDecoderMatcher[String, CategoryNameConstraint]("name")

    HttpRoutes.of {
      case GET -> Root :? nameQueryParamMatcher(catParam) =>
        IO.fromTry(validated(catParam))
          .flatMap(category => categories.get(category))
          .flatMap {
            case Some(category) => Ok(category)
            case None           => NotFound("unknown category name")
          }

      case GET -> Root / "all" => categories.getAll.flatMap(Ok(_))

      case req @ POST -> Root =>
        req
          .as[RawCreateCategory]
          .flatMap(raw => IO.fromTry(validated(raw.asDomain)))
          .flatMap(categories.create)
          .flatMap {
            case Left(CategoryAlreadyExists) => BadRequest("Category already exists")
            case Right(id)                   => Created(id)
          }
    }
  }

  def authorRoutes(authors: Authors): HttpRoutes[IO] = {
    val idQueryParamMatcher = namedQueryParamDecoderMatcher[String, Uuid]("id")

    HttpRoutes.of {
      case GET -> Root :? idQueryParamMatcher(idParam) =>
        IO.fromTry(validated(idParam))
          .flatMap(id => authors.get(id))
          .flatMap {
            case Some(author) => Ok(author)
            case None         => NotFound("unknown author id")
          }

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
    val idQueryParamMatcher = namedQueryParamDecoderMatcher[String, Uuid]("id")

    HttpRoutes.of {
      case GET -> Root :? idQueryParamMatcher(idParam) =>
        IO.fromTry(validated(idParam))
          .flatMap(id => books.get(id))
          .flatMap {
            case Some(book) => Ok(book)
            case None       => NotFound("unknown book id")
          }

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
      refineDetailed[CategoryName](name, "name").toValidatedNel,
      refineDetailed[CategoryDescription](description, "description").toValidatedNel
    ).mapN(CreateCategory)
  }

  case class RawCreateAuthor(firstName: String, lastName: String) {
    def asDomain: ValidatedNel[ParseFailure, CreateAuthor] = (
      refineDetailed[Authors.FirstName](firstName, "firstName").toValidatedNel,
      refineDetailed[Authors.LastName](lastName, "lastName").toValidatedNel
    ).mapN(CreateAuthor)
  }

  case class RawCreateBook(
      title: String,
      authorId: String,
      publicationYear: Option[Int],
      categoryIds: List[String],
      summary: Option[String]
  ) {
    def asDomain: ValidatedNel[ParseFailure, CreateBook] = {
      (
        refineDetailed[Books.BookTitle](title, "title").toValidatedNel,
        refineDetailed[Authors.AuthorId](authorId, "authorId").toValidatedNel,
        refineOptDetailed[Books.BookPublicationYear](publicationYear, "publicationYear").toValidatedNel,
        categoryIds.traverse(catId => refineDetailed[Categories.CategoryId](catId, "categoryIds").toValidatedNel),
        Validated.validNel[ParseFailure, Option[String]](summary)
      ).mapN(CreateBook)

    }
  }

}
