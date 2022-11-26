package bookshelf.utils

import cats.data.ValidatedNel
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import cats.MonadThrow
import cats.effect.unsafe.IORuntime
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.syntax.applicative._
import munit.CatsEffectAssertions
import org.http4s.EntityDecoder
import org.http4s.Response
import org.http4s.Status
import org.http4s.implicits._
import munit.Assertions
import cats.effect.Ref

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

object effect {

  /** Map with an effectful API, handy for mocking some DB with an in-memory mock
    */
  trait EffectMap[F[_], K, V] {
    def getAll: F[List[(K, V)]]
    def getAllValues: F[List[V]]
    def get(key: K): F[Option[V]]
    def add(key: K, value: V): F[K]
    def remove(key: K): F[Unit]
  }

  object EffectMap {
    def make[F[_]: MonadThrow: Ref.Make, K, V](init: Map[K, V] = Map.empty[K, V]): F[EffectMap[F, K, V]] = {
      Ref
        .ofEffect(init.pure[F])
        .map { state =>
          new EffectMap[F, K, V] {
            def getAll: F[List[(K, V)]] = state.get.map(_.toList)
            def getAllValues: F[List[V]] = getAll.map(_.map(_._2))
            def get(key: K): F[Option[V]] = state.get.map(_.get(key))
            def add(key: K, value: V): F[K] = state.update(_ + (key -> value)).as(key)
            def remove(key: K): F[Unit] = state.update(_.removed(key))
          }
        }
    }
  }
}
