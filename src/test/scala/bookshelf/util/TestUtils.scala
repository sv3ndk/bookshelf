package bookshelf.util

import cats.syntax.traverse._
import cats.effect.IO
import org.http4s.Response
import org.http4s.EntityDecoder
import org.http4s.Status
import org.http4s.implicits._
import munit.CatsEffectAssertions
import cats.effect.unsafe.IORuntime

trait TestUtils {

  self: CatsEffectAssertions =>

  def assertOkResponse[A](tested: IO[Response[IO]], expectedBody: A)(implicit decoder: EntityDecoder[IO, A]): IO[Unit] =
    tested
      .flatMap(r =>
        r.status match {
          case Status.Ok =>
            r.as[A].assertEquals(expectedBody)
          case s =>
            r.body
              .through(fs2.text.utf8.decode)
              .compile
              .toList
              .map(_.mkString)
              .flatMap(body =>
                IO.raiseError(new RuntimeException(s"unexpected service error during test: $body, $s }"))
              )
        }
      )

  def assertFailedResponse[A](tested: IO[Response[IO]], expectedStatus: Status, expectedBody: String): IO[Unit] =
    assertIO(tested.map(_.status), expectedStatus) >>
      assertIO(
        tested.flatMap(_.body.through(fs2.text.utf8.decode).compile.toList.map(_.mkString)),
        expectedBody
      )

}
