package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.util.TestUtils
import bookshelf.util.effect.EffectMap
import cats.effect.IO
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
import org.scalacheck.effect.PropF

class CategoryRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  // ----------------------

  def testCategoryRoute(
      testData: Map[Categories.CategoryName, Categories.Category],
      test: HttpApp[IO] => IO[Unit]
  ): IO[Unit] = {
    for {
      mockCategories <- TestData.mockCategoriesService(testData)
      testedApp = ErrorHandling.Recover.messageFailure(new CatalogRoutes[IO].categoryRoutes(mockCategories).orNotFound)
      result <- test(testedApp)
    } yield result
  }

  def testInvalidCategoryRequests(request: Request[IO], expectedStatus: org.http4s.Status, expectedBody: String) = {
    testCategoryRoute(
      Map.empty,
      testedApp => assertFailedResponse(testedApp.run(request), expectedStatus, expectedBody)
    )
  }

  // ----------------------  book category ------------------------------

  // this is just testing the route itself, in the positive scenario
  test("getting a pre-existing book category should work") {
    PropF.forAllF(TestData.fineCategoryGen) { category =>
      testCategoryRoute(
        testData = Map(category.name -> category),
        test = testedApp =>
          assertOkResponse(
            testedApp.run(GET(Uri.unsafeFromString(s"?name=${category.name.value}"))),
            category
          )
      )
    }
  }

  test("invalid category name query param should yield correct error message, without echoing input") {
    testInvalidCategoryRequests(
      GET(uri"?name="),
      Status.BadRequest,
      "Invalid query param 'name': should not be empty"
    )
  }

  test(
    "posting well formed json category with 2 (invalid) empty fields should yield an error about both fields, without echoing input"
  ) {
    testInvalidCategoryRequests(
      POST(CatalogRoutes.RawCategory("", "", ""), uri""),
      Status.BadRequest,
      "id is not a valid UUID, name should not be empty, description should not be empty"
    )
  }

  test("malformed json body should yield correct error message, without echoing input") {
    testInvalidCategoryRequests(
      POST("{ this is not valid JSON", uri"/"),
      Status.UnprocessableEntity,
      "The request body was invalid."
    )
  }

}
