package bookshelf.catalog

import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import bookshelf.util.EffectMap
import cats.effect.kernel.Resource

// TODO: split between edge model and domain model
// TODO: use refined types and cats Validated data type
case class Book(title: String, author: Author, publicationDate: Long, genres: List[Genre], summary: Option[String])
case class Author(firstName: String, lastName: String)
case class Genre(name: String, description: String)

trait Genres[F[_]] {
  def getAll: F[List[Genre]]
  def get(name: String): F[Option[Genre]]
  def add(genre: Genre): F[String]
}

object Genres {
  def make[F[_]: Concurrent]: Resource[F, Genres[F]] =
    Resource.make(
      // TODO: should use PostGres and Doobie instead
      EffectMap
        .make[F, String, Genre]()
        .map { state =>
          new Genres[F] {
            def getAll: F[List[Genre]] = state.getAllValues
            def get(name: String): F[Option[Genre]] = state.get(name)
            def add(genre: Genre): F[String] = state.add(genre.name, genre)
          }
        }
    )(_ => ().pure[F])
}

trait Authors[F[_]] {
  def getAll: F[List[Author]]
}

object Authors {
  def make[F[_]: Concurrent]: Resource[F, Authors[F]] =
    Resource.make(
      // TODO: should use Postgres and Doobie instead
      EffectMap
        .make[F, String, Author]()
        .map { state =>
          new Authors[F] {
            def getAll: F[List[Author]] = state.getAllValues
          }
        }
    )(_ => ().pure[F])
}
