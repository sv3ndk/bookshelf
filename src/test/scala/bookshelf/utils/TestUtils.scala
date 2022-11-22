package bookshelf.utils

import cats.data.ValidatedNel
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.functor._
import cats.syntax.traverse._
import munit.CatsEffectAssertions
import org.http4s.EntityDecoder
import org.http4s.Response
import org.http4s.Status
import org.http4s.implicits._
import munit.Assertions

trait TestUtils {

  self: CatsEffectAssertions with Assertions =>

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
    tested.map(_.status).assertEquals(expectedStatus) *>
      tested.flatMap(response => bodyAsText(response.body)).assertEquals(expectedBody)

  def assertFailedResponse[A](tested: Response[IO], expectedStatus: Status, expectedBody: String): IO[Unit] =
    IO.pure(tested.status).assertEquals(expectedStatus) *>
      assertIO(bodyAsText(tested.body), expectedBody)

}
