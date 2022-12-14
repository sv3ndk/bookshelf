package bookshelf.catalog

import bookshelf.utils.core.TechnicalError
import bookshelf.utils.validation.AsDetailedValidationError
import cats.effect.IO
import cats.syntax.applicative._
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.syntax.functor._
import doobie._
import doobie.implicits._
import doobie.postgres._
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
    SQL.getByName(name).option
  def getAll: ConnectionIO[List[Category]] =
    SQL.getAll.to[List]
  def create(
      id: CategoryId,
      createCategory: CreateCategory
  ): ConnectionIO[Either[CategoryAlreadyExists.type, CategoryId]] =
    SQL
      .create(id, createCategory)
      .run
      .attemptSomeSqlState { case sqlstate.class23.UNIQUE_VIOLATION => CategoryAlreadyExists }
      .map(_.map(_ => id))

  object SQL {

    val getAll = sql"select id, name, description from category".query[Category]

    def getByName(name: CategoryName) =
      sql"""
        select id, name, description 
        from category
        where name = $name
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

  def get(id: AuthorId): ConnectionIO[Option[Author]] = SQL.get(id).option
  def getAll: ConnectionIO[List[Author]] = SQL.getAll.to[List]
  def create(id: AuthorId, createAuthor: CreateAuthor) = SQL.create(id, createAuthor).run.as(id)

  object SQL {
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
    SQL
      .get(id)
      .to[List]
      .map {
        case Nil => None
        case (BookRow(title, year, summary), author, oneCategory) :: moreRows =>
          Some(Book(id, title, author, year, oneCategory.toList ++ moreRows.flatMap(_._3), summary))
      }
  }
  def create(id: BookId, createBook: CreateBook) =
    SQL.create(id, createBook).run >>
      createBook.categoryIds.traverse(categoryId => SQL.addCategory(id, categoryId).run).as(id)

  case class BookRow(title: BookTitle, year: Option[BookPublicationYear], summary: Option[BookSummary])

  object SQL {

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
      order by category.name
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
