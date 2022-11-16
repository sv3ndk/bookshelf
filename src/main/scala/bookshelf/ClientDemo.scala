package bikecfg

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.ExitCode
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.implicits._
import org.http4s.Method
import org.http4s.ember.client.EmberClientBuilder

object ClientDemo extends IOApp {

  def callEffect(client: Client[IO]): IO[String] = {
    val uri = uri"http://localhost:8080/abc"
    val request = Method.GET(uri, Accept(MediaType.application.json))
    client.expect[String](request)
  }

  def run(args: List[String]): IO[ExitCode] = {
    EmberClientBuilder
      .default[IO]
      .build
      .use(client =>
        for {
          result <- callEffect(client).redeem(
            error => "could not get a result",
            something => s"this is what I got: $something"
          )
          _ <- IO.println(result)
        } yield ExitCode.Success
      )
  }
}
