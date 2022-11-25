package bookshelf.catalog

import bookshelf.utils.effect.EffectMap
import bookshelf.utils.validation
import bookshelf.utils.core.{makeId, TechnicalError}
import bookshelf.utils.validation.AsDetailedValidationError
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.effect.syntax.async
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import doobie._
import doobie.implicits._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection._
import eu.timepit.refined.generic._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._

trait Categories {
  def getAll: IO[List[Categories.Category]]
  def get(name: Categories.CategoryName): IO[Option[Categories.Category]]
  def create(category: Categories.CreateCategory): IO[Categories.CategoryId]
}

object Categories {

  type CategoryId = String Refined Uuid
  type CategoryNameConstraint = And[MinSize[1], MaxSize[25]]
  type CategoryName = String Refined CategoryNameConstraint
  implicit val InvalidCategoryNameErr =
    AsDetailedValidationError.forPredicate[CategoryNameConstraint]("must be between 1 and 25 char long")
  type CategoryDescription = String Refined NonEmpty
  case class CreateCategory(name: CategoryName, description: CategoryDescription)
  case class Category(id: CategoryId, name: CategoryName, description: CategoryDescription)

  def make(xa: Transactor[IO]) = new Categories {
    def get(name: CategoryName) = CategoriesDb.get(name).transact(xa)
    def getAll = CategoriesDb.getAll.transact(xa)
    def create(category: CreateCategory) =
      IO.fromEither(makeId[CategoryId])
        .flatMap(id => CategoriesDb.create(id, category).transact(xa))
  }
}

trait Authors {
  def get(id: Authors.AuthorId): IO[Option[Authors.Author]]
  def getAll: IO[List[Authors.Author]]
  def create(createAuthor: Authors.CreateAuthor): IO[Authors.AuthorId]
}

object Authors {

  type AuthorId = String Refined Uuid
  type FirstName = String Refined NonEmpty
  type LastName = String Refined NonEmpty
  case class Author(id: AuthorId, firstName: FirstName, lastName: LastName)
  case class CreateAuthor(firstName: FirstName, lastName: LastName)

  def make(xa: Transactor[IO]) = new Authors {
    def get(id: AuthorId): IO[Option[Authors.Author]] = AuthorsDb.get(id).transact(xa)
    def getAll: IO[List[Author]] = AuthorsDb.getAll.transact(xa)
    def create(createAuthor: Authors.CreateAuthor) =
      IO.fromEither(makeId[AuthorId])
        .flatMap(id => AuthorsDb.create(id, createAuthor).transact(xa))
  }
}

trait Books {
  def get(id: Books.BookId): IO[Option[Books.Book]]
  def create(createBook: Books.CreateBook): IO[Books.BookId]
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
      author: Authors.Author,
      publicationYear: BookPublicationYear,
      categories: List[Categories.Category],
      summary: String
  )
  case class CreateBook(
      title: BookTitle,
      authorId: Authors.AuthorId,
      publicationYear: BookPublicationYear,
      categoryIds: List[Categories.CategoryId],
      summary: String
  )

  def make(xa: Transactor[IO]) = new Books {
    def get(id: BookId): IO[Option[Book]] = BooksDb.get(id).transact(xa)
    def create(createBook: Books.CreateBook) =
      IO.fromEither(makeId[BookId])
        .flatMap(id => BooksDb.create(id, createBook).transact(xa))
  }

}
