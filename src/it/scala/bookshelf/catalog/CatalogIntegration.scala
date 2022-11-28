package bookshelf.clientdemo

import bookshelf.BookshelfServer
import bookshelf.BookshelfServerApp
import bookshelf.Config
import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.catalog.Categories._
import bookshelf.utils.debug._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import com.dimafeng.testcontainers.Container
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.ExposedService
import com.dimafeng.testcontainers.WaitingForService
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.dimafeng.testcontainers.munit.TestContainerForEach
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

class CatalogIntegration extends CatsEffectSuite with TestContainerForAll {

  override val containerDef =
    DockerComposeContainer.Def(
      new File("src/it/resources/docker-compose.yml"),
      tailChildContainers = true,
      exposedServices = Seq(
        ExposedService("postgres_1", 5432, Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
      )
    )

  /** Executes this test within an initialized integration environement, i.e.:
    *   - docker environment is running
    *   - an http client is available pointing to the appropriate url
    *   - DB is initialized with test data
    *
    * The database is reset before every test
    */

  def httpTest(label: String)(testWithClient: BookshelfClient => IO[Unit]) =
    test(label) {
      withContainers(containers => {
        val config = Config(
          rdbmsHost = containers.getServiceHost("postgres_1", 5432),
          rdbmsPort = containers.getServicePort("postgres_1", 5432)
        )
        ClientDemo.populateTestData(config) >>
          BookshelfServer
            .localBookshelfApp(config)
            .map(app => BookshelfClient(Client.fromHttpApp(app)))
            .use(testWithClient)
      })
    }

  // ---------------------------''

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

}
