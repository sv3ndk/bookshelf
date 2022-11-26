package bookshelf.catalog

import bookshelf.utils.core.TechnicalError
import bookshelf.utils.core.makeId
import bookshelf.utils.validation
import bookshelf.utils.validation.AsDetailedValidationError
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.effect.syntax.async
import cats.syntax.applicative._
import cats.syntax.traverse._
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

object CategoriesDb extends doobie.refined.Instances {
  import Categories._

  def get(name: CategoryName): ConnectionIO[Option[Category]] =
    Queries.getByName(name).option
  def getAll: ConnectionIO[List[Category]] =
    Queries.getAll.to[List]
  def create(id: CategoryId, createCategory: CreateCategory): ConnectionIO[CategoryId] =
    Queries.create(id, createCategory).run.as(id)

  object Queries {

    val getAll = sql"select id, name, description from category".query[Category]

    def getByName(name: CategoryName) =
      sql"""
        select id, name, description 
        from category
        where name = $name
        limit 1
        """.query[Category]

    def create(id: CategoryId, createCategory: CreateCategory) =
      sql"""
        insert into category (id, name, description)
        values ($id, ${createCategory.name}, ${createCategory.description})
      """.update
  }

}

object AuthorsDb extends doobie.refined.Instances {
  import Authors._

  def get(id: AuthorId): ConnectionIO[Option[Author]] = Queries.get(id).option
  def getAll: ConnectionIO[List[Author]] = Queries.getAll.to[List]
  def create(id: AuthorId, createAuthor: CreateAuthor) = Queries.create(id, createAuthor).run.as(id)

  object Queries {
    def get(id: AuthorId) = sql"select id, first_name, last_name from author where id = $id".query[Author]
    val getAll = sql"select id, first_name, last_name from author".query[Author]
    def create(id: AuthorId, createAuthor: CreateAuthor) =
      sql"""
        insert into author (id, first_name, last_name)
        values ($id, ${createAuthor.firstName}, ${createAuthor.lastName})
      """.update

  }
}

object BooksDb extends doobie.refined.Instances {
  import Books._
  import Authors._

  def get(id: BookId): ConnectionIO[Option[Book]] = {
    Queries
      .get(id)
      .to[List]
      .map {
        case Nil => None
        case (BookRow(title, year, summary), author, oneCategory) :: moreRows =>
          Some(Book(id, title, author, year, oneCategory.toList ++ moreRows.flatMap(_._3), summary))
      }
  }
  def create(id: BookId, createBook: CreateBook) =
    Queries.create(id, createBook).run >>
      createBook.categoryIds.traverse(categoryId => Queries.addCategory(id, categoryId).run).as(id)

  case class BookRow(title: BookTitle, year: BookPublicationYear, summary: BookSummary)

  object Queries {

    def get(bookId: BookId) =
      sql"""
      select
        book.title, book.publication_year, book.summary,
        author.id, author.first_name, author.last_name,
        category.id, category.name, category.description
      from book
        left join author on book.author_id = author.id
        left join book_category on book.id = book_category.book_id
          left join category on book_category.category_id  = category.id
      where 
        book.id = $bookId
      """.query[(BookRow, Authors.Author, Option[Categories.Category])]

    def create(bookId: BookId, createBook: CreateBook) =
      sql"""
        insert into book (id, title, author_id, publication_year, summary)
        values ($bookId, ${createBook.title}, ${createBook.authorId}, ${createBook.publicationYear}, ${createBook.summary})
      """.update

    def addCategory(bookId: BookId, categoryId: Categories.CategoryId) =
      sql"insert into book_category (book_id, category_id) values ($bookId, $categoryId)".update
  }
}
