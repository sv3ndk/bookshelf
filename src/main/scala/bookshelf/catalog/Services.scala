package bookshelf.catalog

import bookshelf.util.effect.EffectMap
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection._
import eu.timepit.refined.string._
import eu.timepit.refined.boolean._
import eu.timepit.refined.generic._
import eu.timepit.refined.api.Validate
import bookshelf.util.refined
import bookshelf.util.refined.ToDetailedValidationErr

// TODO: split between edge model and domain model
// TODO: use refined types and cats Validated data type
case class Book(
    title: String,
    author: Author,
    publicationDate: Long,
    genres: List[Genres.Genre],
    summary: Option[String]
)
case class Author(firstName: String, lastName: String)

//
trait Genres[F[_]] {
  def getAll: F[List[Genres.Genre]]
  def get(name: Genres.GenreName): F[Option[Genres.Genre]]
  def add(genre: Genres.Genre): F[Genres.GenreName]
}

object Genres {

  implicit val NonEmptyErr =
    ToDetailedValidationErr.forRefined[NonEmpty]("should not be empty")

  type GenreName = String Refined NonEmpty
  type GenreDescription = String Refined NonEmpty

  case class Genre(name: GenreName, genreDescription: GenreDescription)

  type SomethingElse = Int Refined MinSize[3]
  implicit val IgnoredFriendlyError =
    ToDetailedValidationErr.forRefined[MinSize[3]]("Should be larger than 3")

  def make[F[_]: Concurrent]: F[Genres[F]] =
    // TODO: should use PostGres and Doobie instead
    EffectMap
      .make[F, GenreName, Genre]()
      .map { state =>
        new Genres[F] {
          def getAll: F[List[Genre]] = state.getAllValues
          def get(name: GenreName): F[Option[Genre]] = state.get(name)
          def add(genre: Genre): F[GenreName] = state.add(genre.name, genre)
        }
      }
}

trait Authors[F[_]] {
  def getAll: F[List[Author]]
}

object Authors {
  def make[F[_]: Concurrent]: F[Authors[F]] = {
    // TODO: should use Postgres and Doobie instead
    EffectMap
      .make[F, String, Author]()
      .map { state =>
        new Authors[F] {
          def getAll: F[List[Author]] = state.getAllValues
        }
      }
  }
}
