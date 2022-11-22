package bookshelf.catalog

import bookshelf.utils.effect.EffectMap
import bookshelf.utils.validation
import bookshelf.utils.validation.AsDetailedValidationError
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection._
import eu.timepit.refined.generic._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._
import _root_.cats.effect.syntax.async

trait Categories[F[_]] {
  def getAll: F[List[Categories.Category]]
  def get(name: Categories.CategoryName): F[Option[Categories.Category]]
  def add(genre: Categories.Category): F[Categories.CategoryName]
}

object Categories {

  type CategoryId = String Refined Uuid
  type CategoryNameConstraint = And[MinSize[1], MaxSize[25]]
  type CategoryName = String Refined CategoryNameConstraint
  implicit val InvalidCategoryNameErr =
    AsDetailedValidationError.forPredicate[CategoryNameConstraint]("must be between 1 and 25 char long")
  type CategoryDescription = String Refined NonEmpty
  case class Category(id: CategoryId, name: CategoryName, description: CategoryDescription)

  def make[F[_]: Concurrent]: F[Categories[F]] =
    // TODO: replace this with usage of PostGres and Doobie
    EffectMap
      .make[F, CategoryName, Category]()
      .map { state =>
        new Categories[F] {
          def getAll: F[List[Category]] = state.getAllValues
          def get(name: CategoryName): F[Option[Category]] = state.get(name)
          def add(category: Category): F[CategoryName] = state.add(category.name, category)
        }
      }
}

trait Authors[F[_]] {
  def getAll: F[List[Authors.Author]]
}

object Authors {

  type AuthorId = String Refined Uuid
  case class Author(firstName: String, lastName: String)

  def make[F[_]: MonadThrow: Ref.Make]: F[Authors[F]] = {
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

trait Books[F[_]] {
  def add(book: Books.Book): F[Books.BookId]
  def get(id: Books.BookId): F[Option[Books.Book]]
}

object Books {

  type BookId = String Refined Uuid
  type BookTitle = String Refined NonEmpty
  type BookPublicationYear = Int Refined And[Greater[1800], Less[2200]]
  implicit val InvalidPublicationYear =
    AsDetailedValidationError.forPredicate[And[Greater[1800], Less[2200]]]("should be a year in [1800, 2200]")

  type BookSummary = String
  case class Book(
      id: BookId,
      title: BookTitle,
      authorId: Authors.AuthorId,
      publicationYear: BookPublicationYear,
      categories: List[Categories.CategoryId],
      summary: String
  )

  def make[F[_]: MonadThrow: Ref.Make]: F[Books[F]] = {
    // TODO: should use Postgres and Doobie instead
    EffectMap
      .make[F, BookId, Book]()
      .map { state =>
        new Books[F] {
          def add(book: Book) = state.add(book.id, book)
          def get(id: BookId) = state.get(id)
        }
      }
  }

}
