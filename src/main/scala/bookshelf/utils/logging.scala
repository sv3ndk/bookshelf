package bookshelf.utils

import cats.effect.IO
import org.log4s._

object logging {

  implicit class BookshelfIoOps[A](val ioa: IO[A]) extends AnyVal {
    def debug(implicit logger: Logger): IO[A] = ioa.map(a => { logger.info(a.toString()); a })
  }

}
