package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Genre
import bookshelf.catalog.Genres
import bookshelf.util.EffectMap
import bookshelf.util.TestUtils
import cats.effect.IO
import cats.syntax.all._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import munit.ScalaCheckSuite
import org.http4s.Method._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF
import org.scalacheck.Gen

class GenreRouteSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  test("adding and retrieving a genre should work") {
    // this is really just testing the route itself, in the positive scenario
    PropF.forAllF(TestData.genreGen) { genre =>
      for {
        mockGenres <- TestData.mockGenresService(Map(genre.name -> genre))
        testedApp = new CatalogRoutes[IO].genreRoutes(mockGenres).orNotFound
        result <-
          assertOkResponse(
            testedApp.run(Request[IO](GET, Uri.unsafeFromString(s"/${genre.name}"))),
            genre
          )
      } yield result
    }
  }

  // some kind of integration test: creating an additional FrameSize and reading it
  // we could also have built the client from the testedRoute app, or use the Kleisli .run()
  // directly, although this is a tad more "end-to-end" since it relies on serialization as well

  // TODO: make this an actual integration test
  test("mini integration test for Genre creation") {
    BookshelfServer.bikeApp[IO].use { app =>
      val client = Client.fromHttpApp(app)
      val createdGenre = Genre("novel", "written stuff")

      for {
        sizeId <- client.expect[String](Method.POST(createdGenre.asJson, uri"/catalog/genre/novel"))
        readGenre = client.expect[Genre](uri"/catalog/genre/novel")
        result <- assertIO(readGenre, createdGenre)
      } yield result

    }
  }

}

object TestData {
  def mockGenresService(data: Map[String, Genre]): IO[Genres[IO]] =
    EffectMap
      .make[IO, String, Genre](data)
      .map { state =>
        new Genres[IO] {
          def getAll = state.getAllValues
          def get(name: String) = state.get(name)
          def add(genre: Genre) = state.add(genre.name, genre)
        }
      }

  val nonEmptyAlphaNumString: Gen[String] = for {
    first <- Gen.alphaNumChar
    rest <- Gen.alphaNumStr
  } yield s"$first$rest"

  val genreGen: Gen[Genre] = for {
    name <- nonEmptyAlphaNumString
    description <- nonEmptyAlphaNumString
  } yield Genre(name, description)

}
