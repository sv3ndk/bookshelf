package bookshelf

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    BookshelfServer.server[IO].compile.drain.as(ExitCode.Success)
}
