package bookshelf.catalog

import bookshelf.catalog.Books._
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.TestUtils
import bookshelf.utils.authentication.User
import bookshelf.utils.TestAuthentication.dummyAuthMiddlewareAllUsersAdmin
import bookshelf.utils.TestAuthentication.dummyAuthMiddlewareAllUsersWithoutRoles

import bookshelf.utils.core.makeId
import cats.data.Validated
import cats.effect.IO
import cats.instances.finiteDuration
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection._
import eu.timepit.refined.string.Uuid
import io.circe.Json
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import munit.ScalaCheckSuite
import org.http4s.Method._
import org.http4s.Status.BadRequest
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.http4s.server.middleware.ErrorHandling
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF
import org.http4s.server.AuthMiddleware
import bookshelf.utils.authentication

class BookRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  def testedBooksRoutes(
      stub: BooksStub = new BooksStub(),
      auth: AuthMiddleware[IO, User] = dummyAuthMiddlewareAllUsersAdmin
  ) =
    ErrorHandling.Recover.messageFailure(CatalogRoutes.bookRoutes(auth, stub).orNotFound)

  test("getting a pre-existing book category") {
    PropF.forAllF(TestDataGen.fineBooksGen) { testBooks =>
      val someBook = testBooks.head
      assertResponse(
        testedBooksRoutes(new BooksStub(testBooks)).run(GET(uri"/".withQueryParam("id", someBook.id.value))),
        Status.Ok,
        someBook
      )
    }
  }

  test("creating a book should yield an id") {
    PropF.forAllF(TestDataGen.rawCreateBookGen) { createBook =>
      assertResponse(
        testedBooksRoutes().run(POST(createBook, uri"/")),
        Status.Created,
        BooksStub.createdId.value
      )
    }
  }

  test("invalid book id query param should yield correct error message") {
    assertFailedResponse(
      testedBooksRoutes().run(GET(uri"/".withQueryParam("id", "someinvaliduuid"))),
      Status.BadRequest,
      "Invalid query param: id is not a valid UUID"
    )
  }

  test("posting well formed json book with all empty fields should yield all errors, without echoing the input") {
    assertFailedResponse(
      testedBooksRoutes().run(POST(CatalogRoutes.RawCreateBook("", "", Some(0), List("", ""), Some("")), uri"/")),
      Status.BadRequest,
      "title should not be empty, authorId is not a valid UUID, publicationYear should be a year in [1800, 2200], categoryIds is not a valid UUID, categoryIds is not a valid UUID"
    )
  }

  test("creating a book without admin role should be forbidden") {
    PropF.forAllF(TestDataGen.rawCreateBookGen) { createdBook =>
      assertFailedResponse(
        testedBooksRoutes(auth = dummyAuthMiddlewareAllUsersWithoutRoles)
          .run(POST(createdBook, uri"/")),
        Status.Forbidden,
        "\"Only admins can create new books\""
      )
    }
  }

  class BooksStub(data: List[Books.Book] = List.empty) extends Books {
    def get(id: Books.BookId) = IO.pure(data.find(_.id == id))
    def create(createBook: Books.CreateBook) = IO.pure(BooksStub.createdId)
  }

  object BooksStub {
    val createdId = makeId[Books.BookId]
  }

}
