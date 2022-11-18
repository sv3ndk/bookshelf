package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Genres
import bookshelf.util.effect.EffectMap
import bookshelf.util.TestUtils
import cats.effect.IO
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.collection._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Validate
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
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF
import eu.timepit.refined.api.Refined

class GenreRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  test("adding and retrieving a book genre should work") {
    //   // this is really just testing the route itself, in the positive scenario
    PropF.forAllF(TestData.genreGen) { genre =>
      for {
        mockGenres <- TestData.mockGenresService(Map(genre.name -> genre))
        testedApp = new CatalogRoutes[IO].genreRoutes(mockGenres).orNotFound
        result <-
          assertOkResponse(
            testedApp.run(Request[IO](GET, Uri.unsafeFromString(s"?name=${genre.name.value}"))),
            genre
          )
      } yield result
    }
  }

  // // some kind of integration test: creating an additional FrameSize and reading it
  // // we could also have built the client from the testedRoute app, or use the Kleisli .run()
  // // directly, although this is a tad more "end-to-end" since it relies on serialization as well

  // // TODO: make this an actual integration test
  test("mini integration test for Genre creation") {
    BookshelfServer.bikeApp[IO].use { app =>
      val client = Client.fromHttpApp(app)
      val novel = RefType.applyRef[Genres.GenreName]("novel").toOption.get
      val description = RefType.applyRef[Genres.GenreDescription]("bla bla bla").toOption.get
      val createdGenre = Genres.Genre(novel, description)

      for {
        sizeId <- client.expect[String](Method.POST(createdGenre.asJson, uri"/catalog/genre"))
        readGenre = client.expect[Genres.Genre](uri"/catalog/genre?name=novel")
        result <- assertIO(readGenre, createdGenre)
      } yield result

    }
  }

  test("test invalid genreName") {
    for {
      mockGenres <- TestData.mockGenresService(Map.empty)
      testedApp = new CatalogRoutes[IO].genreRoutes(mockGenres).orNotFound
      result <-
        assertFailedResponse(
          testedApp.run(Request[IO](GET, Uri.unsafeFromString(s"?name="))),
          Status.BadRequest,
          "\"Invalid query param 'name': should not be empty\""
        )
    } yield result
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
