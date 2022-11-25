package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.Books._
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.TestUtils
import bookshelf.utils.effect.EffectMap
import cats.effect.IO
import cats.instances.finiteDuration
import cats.data.Validated
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
import org.scalacheck.Prop._
import org.scalacheck.Prop
import org.scalacheck.effect.PropF

class BookRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  // ----------------------

  def testBookRoute(
      testData: Map[Books.BookId, Books.Book],
      test: HttpApp[IO] => IO[Unit]
  ): IO[Unit] = {
    for {
      mockBooksService <- TestData.mockBooksService(testData)
      testedApp = ErrorHandling.Recover.messageFailure(CatalogRoutes.bookRoutes(mockBooksService).orNotFound)
      result <- test(testedApp)
    } yield result
  }

  def testInvalidBookRequests(request: Request[IO], expectedStatus: org.http4s.Status, expectedBody: String) = {
    testBookRoute(
      Map.empty,
      testedApp => assertFailedResponse(testedApp.run(request), expectedStatus, expectedBody)
    )
  }

  // ----------------------  book category ------------------------------

  // this is just testing the route itself, in the positive scenario
  test("getting a pre-existing book category should work") {
    PropF.forAllF(TestData.fineBooksGen) { testBooks =>
      val (bookId, book) = testBooks.head
      testBookRoute(
        testData = testBooks,
        test = testedApp =>
          assertOkResponse(
            testedApp.run(GET(Uri.unsafeFromString(s"?id=${bookId.value}"))),
            book
          )
      )
    }
  }

  test("invalid book id query param should yield correct error message") {
    testInvalidBookRequests(
      GET(uri"?id=someinvaliduuid"),
      Status.BadRequest,
      "Invalid query param 'id': is not a valid UUID"
    )
  }

  test("posting well formed json category with all (invalid) empty fields should yield an error about both fields") {
    testInvalidBookRequests(
      POST(Book("", "", "", 0, List("", ""), ""), uri""),
      Status.BadRequest,
      "id is not a valid UUID, title should not be empty, authorId is not a valid UUID, publicationYear should be a year in [1800, 2200], categories is not a valid UUID, categories is not a valid UUID"
    )
  }

  // ---------------------------------- data validation ---------------------------

  test("valid raw book should correctly be converted to domain") {
    import CatalogRoutes._
    Prop.forAll(TestData.fineBookGen) { fineBook =>
      val rawBook = new Book(
        fineBook.id.value,
        fineBook.title.value,
        fineBook.authorId.value,
        fineBook.publicationYear.value,
        fineBook.categories.map(_.value),
        fineBook.summary
      )
      assertEquals(rawBook.asDomain, Validated.valid(fineBook))
    }
  }

}
