package bookshelf.utils

import cats.effect.IO
import munit.Assertions
import munit.CatsEffectAssertions
import org.http4s.EntityDecoder
import org.http4s.Response
import org.http4s.Status

trait TestUtils {

  self: CatsEffectAssertions with Assertions =>

  def bodyAsText(body: fs2.Stream[IO, Byte]): IO[String] =
    body
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)

  def assertResponse[A](tested: IO[Response[IO]], expectedStatus: Status, expectedBody: A)(implicit
      decoder: EntityDecoder[IO, A]
  ): IO[Unit] =
    tested
      .flatMap(response =>
        response.status match {
          case `expectedStatus` => response.as[A].assertEquals(expectedBody)
          case unexpectedStatus =>
            bodyAsText(response.body).flatMap(body =>
              IO.raiseError(new RuntimeException(s"unexpected service error during test: $unexpectedStatus, $body }"))
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
