package bookshelf.clientdemo

import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.catalog.Categories._
import bookshelf.utils.debug._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
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

object ClientDemo extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    BookshelfClient
      .build(uri"http://localhost:8080/")
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
              List(
                catId,
                refineMV("9d151f50-44ca-44f0-aec4-f06daf6b3659")
              ),
              "In Part Two of Chris Pountney’s attempt to cycle around the world, a disaster in the Pacific Ocean soon forces a drastic change of plans."
            )
          )
          _ <- client.getBook(bookId).debug
        } yield ExitCode.Success
      )
  }
}
