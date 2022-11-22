package bookshelf.catalog

import bookshelf.BookshelfServer
import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Categories
import bookshelf.utils.TestUtils
import bookshelf.utils.effect.EffectMap
import cats.effect.IO
import cats.syntax.all._
import eu.timepit.refined._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
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
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF

// embryo of integration test, uring the whole app as built from BookshelfServer.bookshelfApp[IO]

// some kind of integration test: creating an additional FrameSize and reading it
// we could also have built the client from the testedRoute app, or use the Kleisli .run()
// directly, although this is a tad more "end-to-end" since it relies on serialization as well

class WebAppSpec extends CatsEffectSuite with TestUtils with ScalaCheckEffectSuite {

  def testViaApp(test: Client[IO] => IO[Unit]): IO[Unit] = {
    // TODO: we probably want to fully initialize the app with a bunch of mocked services here
    BookshelfServer.bookshelfApp[IO].use { testedApp =>
      val client = Client.fromHttpApp(testedApp)
      test(client)
    }
  }

  test("mini integration test: posting then retrieving a book category should work") {
    testViaApp(client => {
      val id = Refined.unsafeApply[String, Uuid]("064617f2-acb0-4db6-a142-04366f3b5ad7")
      val name = Refined.unsafeApply[String, Categories.CategoryNameConstraint]("novel")
      val description = Refined.unsafeApply[String, NonEmpty]("bla bla bla")
      val createdGenre = Categories.Category(id, name, description)
      for {
        genreId <- client.expect[String](Method.POST(createdGenre.asJson, uri"/catalog/category"))
        readGenre = client.expect[Categories.Category](uri"/catalog/category?name=novel")
        result <- assertIO(readGenre, createdGenre)
      } yield result
    })
  }

}
