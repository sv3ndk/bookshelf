package bookshelf.clientdemo

import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.BookshelfServer
import bookshelf.catalog.Books._
import bookshelf.catalog.Categories._
import bookshelf.utils.debug._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.dimafeng.testcontainers.munit.TestContainerForEach
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import munit.CatsEffectAssertions
import munit.FunSuite
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

import java.io.File
import munit.CatsEffectSuite
import com.dimafeng.testcontainers.WaitingForService
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import bookshelf.BookshelfServerApp

class CatalogIntegration extends CatsEffectSuite with TestContainerForAll {

  override val containerDef =
    DockerComposeContainer.Def(
      new File("src/it/resources/docker-compose.yml"),
      tailChildContainers = true,
      localCompose = true,
      waitingFor = Some(
        WaitingForService("postgres_1", Wait.forLogMessage(".*ANALYZE.*", 1))
      )
    )

  val clientResource = BookshelfServer.localBookshelfApp.map(app => BookshelfClient.apply(Client.fromHttpApp(app)))

  test("creating and retrieving one Author") {
    val created: IO[Author] =
      clientResource
        .use { client =>
          client
            .createAuthor(CreateAuthor(refineMV("Keyes"), refineMV("Daniel")))
            .flatMap(id => client.getAuthor(id))
        }
    created.map(_.firstName.value).assertEquals("Keyes") *>
      created.map(_.lastName.value).assertEquals("Daniel")
  }

}
