package bookshelf.catalog

import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.TestUtils
import bookshelf.utils.core
import bookshelf.utils.core.makeId
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
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
import org.scalacheck.Prop
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF

class CategoryRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  def testedCategoryRoutes(stub: CategoriesStub = new CategoriesStub()) =
    ErrorHandling.Recover.messageFailure(CatalogRoutes.categoryRoutes(stub).orNotFound)

  test("getting a pre-existing book category") {
    PropF.forAllF(TestDataGen.fineCategoriesGen) { testCategories =>
      val someCategory = testCategories.head
      assertResponse(
        testedCategoryRoutes(new CategoriesStub(testCategories))
          .run(GET(uri"/".withQueryParam("name", someCategory.name.value))),
        Status.Ok,
        someCategory
      )
    }
  }

  test("getting all pre-existing books categories") {
    PropF.forAllF(TestDataGen.fineCategoriesGen) { testCategories =>
      assertResponse(
        testedCategoryRoutes(new CategoriesStub(testCategories)).run(GET(uri"/all")),
        Status.Ok,
        testCategories
      )
    }
  }

  test("creating a category with a valid request") {
    PropF.forAllF(TestDataGen.rawCreateCategoryGen) { createCategory =>
      assertResponse(
        testedCategoryRoutes().run(POST(createCategory, uri"/")),
        Status.Created,
        CategoriesStub.createdId.value
      )
    }
  }

  test("creating a category with no name should fail without echoing the input back") {
    PropF.forAllF(TestDataGen.rawCreateCategoryGen) { createCategory =>
      assertFailedResponse(
        testedCategoryRoutes().run(POST(createCategory.copy(name = ""), uri"/")),
        Status.BadRequest,
        "name must be between 1 and 25 char long"
      )
    }
  }

  test("creating a category with no description should fail without echoing the input back") {
    PropF.forAllF(TestDataGen.rawCreateCategoryGen) { createCategory =>
      assertFailedResponse(
        testedCategoryRoutes().run(POST(createCategory.copy(description = ""), uri"/")),
        Status.BadRequest,
        "description should not be empty"
      )
    }
  }

  test("creating a category with 2 empty field should fail with both errors without echoing the input back") {
    assertFailedResponse(
      testedCategoryRoutes().run(POST(CatalogRoutes.RawCreateCategory("", ""), uri"/")),
      Status.BadRequest,
      "name must be between 1 and 25 char long, description should not be empty"
    )
  }

  test("creating a category with 2 empty field should fail with both errors without echoing the input back") {
    assertFailedResponse(
      testedCategoryRoutes().run(POST("{ this is not valid JSON", uri"/")),
      Status.UnprocessableEntity,
      "The request body was invalid."
    )
  }

  class CategoriesStub(data: List[Categories.Category] = List.empty) extends Categories {
    def get(name: Categories.CategoryName) = IO.pure(data.find(_.name == name))
    def getAll = IO.pure(data)
    def create(category: Categories.CreateCategory) = IO.pure(CategoriesStub.createdId)
  }

  object CategoriesStub {
    val createdId = makeId[Categories.CategoryId].toOption.get
  }

}
