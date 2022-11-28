package bookshelf.catalog

import bookshelf.AppConfig
import bookshelf.BookshelfServer
import bookshelf.clientdemo.BookshelfClient
import bookshelf.clientdemo.ClientDemo
import cats.effect.IO
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.ExposedService
import com.dimafeng.testcontainers.munit.TestContainerForAll
import doobie.implicits._
import doobie.util.Colors
import doobie.util.testing.Analyzable
import doobie.util.testing.analyze
import doobie.util.testing.formatReport
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uuid
import munit.FunSuite
import org.http4s.client.Client
import org.testcontainers.containers.wait.strategy.Wait

import java.io.File

/** Utility trait adding convenience method for running integration tests on the Bookshelf app relying on a
  * docker-compose environment
  */
trait DockerComposeIntegrationTests extends TestContainerForAll {

  self: FunSuite =>

  override val containerDef =
    DockerComposeContainer.Def(
      new File("src/it/resources/docker-compose.yml"),
      tailChildContainers = true,
      exposedServices = Seq(
        ExposedService("postgres_1", 5432, Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
      )
    )

  /** Executes this test relying on a docker-compose environement, reachable as described in the AppConfig
    */
  def testWithContainer(testLabel: String)(testWithConfig: AppConfig => IO[Unit]) =
    test(testLabel) {
      withContainers(containers =>
        testWithConfig(
          AppConfig(
            rdbmsHost = containers.getServiceHost("postgres_1", 5432),
            rdbmsPort = containers.getServicePort("postgres_1", 5432)
          )
        )
      )
    }

  /** Executes this integration test using the app http client, relying an initialized docker-compose environement,
    * i.e.:
    *   - docker environment is running
    *   - the provide http client point to app to test
    *   - a DB is initialized with some test data, and is reset before each test execution
    */
  def httpTest(testLabel: String)(testWithClient: BookshelfClient => IO[Unit]) =
    testWithContainer(testLabel) { appConfig =>
      ClientDemo.resetDbTestData(appConfig) >>
        BookshelfServer
          .localBookshelfApp(appConfig)
          .map(app => BookshelfClient(Client.fromHttpApp(app)))
          .use(testWithClient)
    }

  /** Checks this Doobie query or update against a live connection to the Postgres running env.
    *
    * This is mostly inspired from doobie.munit.analysisspec.Checker.check(), but without requiring to define a
    * transactor as member of the test-suite, in accordance with the withContainers() syntax of testcontainer-scala
    */
  def doobieSqlCheck[A: Analyzable](testedSql: A) = {
    val analysisArg = Analyzable.unpack(testedSql)
    testWithContainer(s"Doobie SQL statement check for ${analysisArg.cleanedSql}") { appConfig =>
      BookshelfServer.localHostTransactor(appConfig).use { xa =>
        analyze(analysisArg)
          .transact(xa)
          .map { report =>
            if (!report.succeeded)
              fail(formatReport(analysisArg, report, Colors.Ansi).padLeft("  ").toString)
          }
      }
    }
  }

  /** Some meaningless though constant UUID
    */
  def trivalId[A](implicit ev: Refined[String, Uuid] =:= A): A =
    refineMV[Uuid]("00000000-0000-0000-0000-000000000000")
}
