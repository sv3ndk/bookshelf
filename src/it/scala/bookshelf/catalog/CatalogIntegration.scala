package bookshelf.clientdemo

import bookshelf.AppConfig
import bookshelf.BookshelfServer
import bookshelf.BookshelfServerApp
import bookshelf.catalog.Authors._
import bookshelf.catalog.AuthorsDb
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.catalog.BooksDb
import bookshelf.catalog.Categories._
import bookshelf.catalog.CategoriesDb
import bookshelf.catalog.DockerComposeIntegrationTests
import bookshelf.utils.debug._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.syntax.all._
import cats.syntax.apply._
import eu.timepit.refined._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string._
import io.circe.generic.auto._
import io.circe.refined._
import munit.CatsEffectAssertions
import munit.CatsEffectSuite
import munit.FunSuite
import munit.ScalaCheckEffectSuite
import org.http4s.EntityEncoder
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers._
import org.http4s.implicits._
import org.scalacheck.effect.PropF
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategy

import java.io.File

class CatalogIntegration extends CatsEffectSuite with DockerComposeIntegrationTests {

  // ---- DB query validity tests

  doobieSqlCheck(CategoriesDb.SQL.getByName(refineMV("anyName")))
  doobieSqlCheck(CategoriesDb.SQL.getAll)
  doobieSqlCheck(
    CategoriesDb.SQL.create(trivalId, CreateCategory(refineMV("someName"), refineMV("anyDescription")))
  )

  doobieSqlCheck(AuthorsDb.SQL.get(trivalId))
  doobieSqlCheck(AuthorsDb.SQL.getAll)
  doobieSqlCheck(
    AuthorsDb.SQL.create(trivalId, CreateAuthor(refineMV("anything"), refineMV("anything")))
  )

  doobieSqlCheck(BooksDb.SQL.get(trivalId))

  // ----- Integration test scenario

  httpTest("creating and retrieving one Author") { httpClient =>
    val createAuthor = CreateAuthor(refineMV("Keyes"), refineMV("Daniel"))
    for {
      authorId <- httpClient.createAuthor(createAuthor)
      actual <- httpClient.getAuthor(authorId)
      assertions = {
        assertEquals(actual, Author(actual.id, createAuthor.firstName, createAuthor.lastName))
        assert(actual.id.value.nonEmpty)
      }
    } yield IO(assertions).void
  }

  httpTest("creating and retrieving one Book Category") { httpClient =>
    val testCategory = CreateCategory(refineMV("Crosswords"), refineMV("Never gives up hope in 7 letters"))
    for {
      categoryId <- httpClient.createCategory(testCategory)
      actual <- httpClient.getCategory(testCategory.name)
      assertions = {
        assertEquals(actual, Category(actual.id, testCategory.name, testCategory.description))
        assert(actual.id.value.nonEmpty)
      }
    } yield IO(assertions).void
  }

  httpTest("create one book with 2 new and 1 existing category and a new author") { httpClient =>
    val author = CreateAuthor(refineMV("Baricco"), refineMV("Alessandro"))
    val categoryLocal = CreateCategory(refineMV("Local"), refineMV("c'est arrivé près de chez vous"))
    val categoryEuropean = CreateCategory(refineMV("European"), refineMV("Litterature from the social continent"))

    for {
      authorId <- httpClient.createAuthor(author)
      localCategoryId <- httpClient.createCategory(categoryLocal)
      europeanCategoryId <- httpClient.createCategory(categoryEuropean)
      novelCategory <- httpClient.getCategory(refineMV("novel"))
      bookid <- httpClient.createBook(
        CreateBook(
          refineMV("Mr Gwyn"),
          authorId,
          Some(refineMV(2011)),
          List(europeanCategoryId, localCategoryId, novelCategory.id),
          Some("The celebrated author Jasper Gwyn suddenly and publicly vows never to write another book.")
        )
      )
      actual <- httpClient.getBook(bookid)
      assertions = {
        assertEquals(
          actual.copy(id = trivalId),
          Book(
            trivalId,
            refineMV("Mr Gwyn"),
            Author(authorId, author.firstName, author.lastName),
            Some(refineMV(2011)),
            List(
              Category(europeanCategoryId, categoryEuropean.name, categoryEuropean.description),
              Category(localCategoryId, categoryLocal.name, categoryLocal.description),
              novelCategory
            ),
            Some("The celebrated author Jasper Gwyn suddenly and publicly vows never to write another book.")
          )
        )
        assert(actual.id.value.nonEmpty)
      }
    } yield IO(assertions).void

  }

}