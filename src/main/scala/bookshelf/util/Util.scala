package bookshelf.util

import cats.MonadThrow
import cats.effect.Ref
import cats.syntax.applicative._
import cats.syntax.functor._

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
  def make[F[_]: MonadThrow: Ref.Make, K, V]: F[EffectMap[F, K, V]] = {
    Ref
      .ofEffect[F, Map[K, V]](Map.empty.pure[F])
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
