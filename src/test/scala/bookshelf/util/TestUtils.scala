package bookshelf.util

import cats.syntax.traverse._
import cats.syntax.functor._
import cats.effect.IO
import org.http4s.Response
import org.http4s.EntityDecoder
import org.http4s.Status
import org.http4s.implicits._
import munit.CatsEffectAssertions
import cats.effect.unsafe.IORuntime

trait TestUtils {

  self: CatsEffectAssertions =>

  def bodyAsText(body: fs2.Stream[IO, Byte]): IO[String] =
    body
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)

  def assertOkResponse[A](tested: IO[Response[IO]], expectedBody: A)(implicit decoder: EntityDecoder[IO, A]): IO[Unit] =
    tested
      .flatMap(response =>
        response.status match {
          case Status.Ok =>
            response.as[A].assertEquals(expectedBody)
          case failedStatus =>
            bodyAsText(response.body).flatMap(body =>
              IO.raiseError(new RuntimeException(s"unexpected service error during test: $failedStatus, $body }"))
            )
        }
      )

  def assertFailedResponse[A](tested: IO[Response[IO]], expectedStatus: Status, expectedBody: String): IO[Unit] =
    assertIO(tested.map(_.status), expectedStatus) *>
      assertIO(
        tested.flatMap(response => bodyAsText(response.body)),
        expectedBody
      )

  def assertFailedResponse[A](tested: Response[IO], expectedStatus: Status, expectedBody: String): IO[Unit] =
    assertIO(IO.pure(tested.status), expectedStatus) *>
      assertIO(bodyAsText(tested.body), expectedBody)

}
