package bookshelf.utils

import cats.effect.IO
import org.log4s._

object logging {

  implicit class BookshelfIoOps[A](val ioa: IO[A]) extends AnyVal {
    def debug(implicit logger: Logger): IO[A] = ioa.flatMap(a => IO.delay(logger.info(a.toString)).as(a))
  }

}
