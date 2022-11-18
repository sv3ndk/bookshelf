package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Genres
import bookshelf.util.TestUtils
import bookshelf.util.effect.EffectMap
import cats.effect.IO
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.collection._
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

class GenreRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  // ----------------------

  def testRoute(testData: Map[Genres.GenreName, Genres.Genre], test: HttpApp[IO] => IO[Unit]): IO[Unit] = {
    for {
      mockGenres <- TestData.mockGenresService(testData)
      testedApp = ErrorHandling.Recover.messageFailure(new CatalogRoutes[IO].genreRoutes(mockGenres).orNotFound)
      result <-
        test(testedApp)
    } yield result
  }

  def testInvalidRequest(request: Request[IO], expectedStatus: org.http4s.Status, expectedBody: String) = {
    testRoute(
      Map.empty,
      testedApp => assertFailedResponse(testedApp.run(request), expectedStatus, expectedBody)
    )
  }

  // ----------------------

  // this is just testing the route itself, in the positive scenario
  test("getting a pre-existing book genre should work") {
    PropF.forAllF(TestData.genreGen) { genre =>
      testRoute(
        testData = Map(genre.name -> genre),
        test = testedApp =>
          assertOkResponse(
            testedApp.run(GET(Uri.unsafeFromString(s"?name=${genre.name.value}"))),
            genre
          )
      )
    }
  }

  test("invalid genreName query param should yield correct error message") {
    testInvalidRequest(
      GET(uri"?name="),
      Status.BadRequest,
      "Invalid query param 'name': should not be empty"
    )
  }

  test("posting well formed json genre with 2 (invalid) empty fields should yield an error about both fields") {
    testInvalidRequest(
      POST(CatalogRoutes.WGenre("", ""), uri""),
      Status.BadRequest,
      "Invalid message body: genreName should not be empty, description should not be empty"
    )
  }

  test("malformed json body should yield correct error message") {
    testInvalidRequest(
      POST("{ this is not valid JSON", uri"/"),
      Status.UnprocessableEntity,
      "The request body was invalid."
    )
  }

}

object TestData {
  def mockGenresService(data: Map[Genres.GenreName, Genres.Genre]): IO[Genres[IO]] =
    EffectMap
      .make[IO, Genres.GenreName, Genres.Genre](data)
      .map { state =>
        new Genres[IO] {
          def getAll = state.getAllValues
          def get(name: Genres.GenreName) = state.get(name)
          def add(genre: Genres.Genre) = state.add(genre.name, genre)
        }
      }

  val nonEmptyAlphaNumString: Gen[String] = for {
    first <- Gen.alphaNumChar
    rest <- Gen.alphaNumStr
  } yield s"$first$rest"

  val genGenreName: Gen[Genres.GenreName] =
    nonEmptyAlphaNumString
      .map(RefType.applyRef[Genres.GenreName](_).toOption)
      .filter(_.isDefined)
      .map(_.get)

  val genNonEmpytString: Gen[String Refined NonEmpty] = {
    type nes = String Refined NonEmpty
    nonEmptyAlphaNumString
      .map(a => refineV[NonEmpty](a).toOption)
      .filter(_.isDefined)
      .map(_.get)
  }

  val genreGen: Gen[Genres.Genre] = for {
    name <- genGenreName
    description <- genNonEmpytString
  } yield Genres.Genre(name, description)

}
