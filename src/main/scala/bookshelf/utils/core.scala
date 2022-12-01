package bookshelf.utils

import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uuid

object core {

  class TechnicalError(err: String) extends RuntimeException

  def makeId[A](implicit ev: Refined[String, Uuid] =:= A): A = {
    val Right(id) = refineV[Uuid](java.util.UUID.randomUUID().toString())
    id
  }
}
