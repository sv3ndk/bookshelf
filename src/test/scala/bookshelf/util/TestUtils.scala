package bookshelf.util
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
          case s => IO.raiseError(new RuntimeException(s"service error: $s}"))
        }
      )

}
