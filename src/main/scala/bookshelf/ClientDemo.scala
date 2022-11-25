package bookshelf.clientdemo

import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.catalog.Categories._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
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
import org.http4s.EntityEncoder

object ClientDemo extends IOApp {

  implicit class MyIoOps[A](val ioa: IO[A]) extends AnyVal {
    def debug: IO[A] = ioa.map(a => { println(s"$a\n"); a })
  }

  def run(args: List[String]): IO[ExitCode] = {
    EmberClientBuilder
      .default[IO]
      .build
      .map(BookshelfClient(_, uri"http://localhost:8080/"))
      .use(client =>
        for {
          _ <- client.getAuthor(refineMV("d9344e5b-1c16-420f-9824-1f03277b32fe")).debug
          _ <- client.getAllAuthors.debug
          // TODO: BUG: non existring category name leads to client failing to parse json
          _ <- client.getCategory("novel").debug
          _ <- client.getAllCategories.debug
          _ <- client.getBook(refineMV("442cda93-5117-412d-9c87-cf9cc73bfad7")).debug
          catId <- client.createCategory(CreateCategory(refineMV("travel"), refineMV("be somewhere else today"))).debug
          _ <- client.getCategory("gardening").debug
          authorId <- client.createAuthor(CreateAuthor(refineMV("Chris"), refineMV("Pountney")))
          _ <- client.getAuthor(authorId).debug
          bookId <- client.createBook(
            CreateBook(
              refineMV("Into the Sunshine"),
              authorId,
              refineMV(2018),
              List(catId),
              "In Part Two of Chris Pountney’s attempt to cycle around the world, a disaster in the Pacific Ocean soon forces a drastic change of plans."
            )
          )
          _ <- client.getBook(bookId).debug
        } yield ExitCode.Success
      )
  }
}

trait BookshelfClient {
  def getCategory(name: String): IO[Category]
  def getAllCategories: IO[List[Category]]
  def createCategory(createCategory: CreateCategory): IO[CategoryId]
  def getAuthor(id: AuthorId): IO[Author]
  def getAllAuthors: IO[List[Author]]
  def createAuthor(createAuthor: CreateAuthor): IO[AuthorId]
  def getBook(id: BookId): IO[Book]
  def createBook(createBook: CreateBook): IO[BookId]
}

object BookshelfClient {
  def apply(httpClient: Client[IO], baseUri: Uri): BookshelfClient = new BookshelfClient {
    val catalogUri = baseUri / "catalog"

    def GET(uri: Uri): Request[IO] = Method.GET(uri, Accept(MediaType.application.json))
    def POST[B](body: B, uri: Uri)(implicit ec: EntityEncoder[IO, B]): Request[IO] =
      Method.POST(body, uri, Accept(MediaType.application.json))

    def getCategory(name: String) =
      IO.println(s"fetching category $name") >>
        httpClient.expect[Category](GET(catalogUri / "category" +? ("name", name)))
    val getAllCategories =
      IO.println(s"fetching all categories") >> httpClient.expect[List[Category]](GET(catalogUri / "category" / "all"))

    def createCategory(createCategory: CreateCategory) =
      httpClient.expect[CategoryId](POST(createCategory, catalogUri / "category"))

    def getAuthor(id: AuthorId) =
      IO.println(s"fetching author $id") >>
        httpClient.expect[Author](GET(catalogUri / "author" +? ("id", id.value)))
    val getAllAuthors =
      IO.println(s"fetching all authors") >>
        httpClient.expect[List[Author]](GET(catalogUri / "author" / "all"))

    def createAuthor(createAuthor: CreateAuthor) =
      httpClient.expect[AuthorId](POST(createAuthor, catalogUri / "author"))

    def getBook(id: BookId) =
      IO.println(s"fetching book $id") >>
        httpClient.expect[Book](GET(catalogUri / "book" +? ("id", id.value)))

    def createBook(createBook: CreateBook) =
      httpClient.expect[BookId](POST(createBook, catalogUri / "book"))

  }
}
