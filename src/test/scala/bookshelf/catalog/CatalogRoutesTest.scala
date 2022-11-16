package bookshelf

import cats.effect.IO
import cats.syntax.all._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.implicits._

import bookshelf.catalog.CatalogRoutes
import bookshelf.catalog.Genre

class FramesSpec extends CatsEffectSuite {

  // def testedRoute: HttpApp[IO] = CatalogRoutes.frameRoutes[IO].orNotFound
  // def ioGet[T](targetUri: Uri): Request[IO] =
  //   Request[IO](Method.GET, targetUri)

  // test("getting all frames sizes in catalog") {
  //   assertIO(
  //     testedRoute.run(ioGet(uri"/size/all")).flatMap(_.as[List[FrameSize]]),
  //     CatalogRoutes.sizes.toList.sortBy(_._1).map(_._2)
  //   )
  // }

  // test("getting one frame size") {
  //   assertIO(
  //     testedRoute.run(ioGet(uri"/size/42")).flatMap(_.as[FrameSize]),
  //     FrameSize(367.2, 497.2, 70)
  //   )
  // }

  // some kind of integration test: creating an additional FrameSize and reading it
  // we could also have built the client from the testedRoute app, or use the Kleisli .run()
  // directly, although this is a tad more "end-to-end" since it relies on serialization as well

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
