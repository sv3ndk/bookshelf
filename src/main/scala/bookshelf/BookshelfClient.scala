package bookshelf.clientdemo

import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.utils.logging._
import bookshelf.catalog.Categories._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.kernel.Resource
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.EntityEncoder
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers._
import org.http4s.implicits._
import org.log4s._
import org.http4s.Response

trait BookshelfClient {
  def getCategory(name: CategoryName): IO[Category]
  def getAllCategories: IO[List[Category]]
  def createCategory(createCategory: CreateCategory): IO[CategoryId]
  def getAuthor(id: AuthorId): IO[Author]
  def getAllAuthors: IO[List[Author]]
  def createAuthor(createAuthor: CreateAuthor): IO[AuthorId]
  def getBook(id: BookId): IO[Book]
  def createBook(createBook: CreateBook): IO[BookId]
}

object BookshelfClient {
  implicit private val logger = getLogger

  case class FailedResponse(response: Response[IO]) extends RuntimeException

  def build(baseUri: Uri): Resource[IO, BookshelfClient] =
    EmberClientBuilder
      .default[IO]
      .build
      .map(BookshelfClient(_, baseUri))

  def apply(httpClient: Client[IO], baseUri: Uri = uri""): BookshelfClient = new BookshelfClient {
    val catalogUri = baseUri / "catalog"

    def GET(uri: Uri): Request[IO] = Method.GET(uri, Accept(MediaType.application.json))
    def POST[B](body: B, uri: Uri)(implicit ec: EntityEncoder[IO, B]): Request[IO] =
      Method.POST(body, uri, Accept(MediaType.application.json))

    def getCategory(name: CategoryName) =
      IO(logger.info(s"fetching category $name")) >>
        httpClient.expect[Category](GET(catalogUri / "category" +? ("name", name.value))).debug
    val getAllCategories =
      IO(logger.info(s"fetching all categories")) >>
        httpClient.expect[List[Category]](GET(catalogUri / "category" / "all")).debug

    def createCategory(createCategory: CreateCategory) =
      IO(logger.info(s"creating  category $createCategory")) >>
        httpClient.expect[CategoryId](POST(createCategory, catalogUri / "category")).debug

    def getAuthor(id: AuthorId) =
      IO(logger.info(s"fetching author $id")) >>
        httpClient.expect[Author](GET(catalogUri / "author" +? ("id", id.value))).debug

    val getAllAuthors =
      IO(logger.info(s"fetching all authors")) >>
        httpClient.expect[List[Author]](GET(catalogUri / "author" / "all")).debug

    def createAuthor(createAuthor: CreateAuthor) =
      IO(logger.info(s"creating author $createAuthor")) >>
        httpClient.expect[AuthorId](POST(createAuthor, catalogUri / "author")).debug

    def getBook(id: BookId) =
      IO(logger.info(s"fetching book $id")) >>
        httpClient.expect[Book](GET(catalogUri / "book" +? ("id", id.value))).debug

    def createBook(createBook: CreateBook) =
      IO(logger.info(s"creating book $createBook")) >>
        httpClient.expect[BookId](POST(createBook, catalogUri / "book")).debug
  }
}
