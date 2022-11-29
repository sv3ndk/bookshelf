package bookshelf.utils

import cats.data.EitherT
import cats.data.OptionT
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.effect.IO
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.option._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uuid
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.QueryParameterValue

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object validation {

  object CommonErrorMessages {
    implicit val InvalidUuid = AsDetailedValidationError.forPredicate[Uuid]("is not a valid UUID")
    implicit val UnexpectedlyEmpty = AsDetailedValidationError.forPredicate[NonEmpty]("should not be empty")
  }

  // provider of a detailed error for predicate P given some String technical error
  trait AsDetailedValidationError[P] {
    def apply(sanitizedPrefix: String, technicalMsg: String): ParseFailure
  }

  // Decorates a Refined Predicate P with a human-friendly error description, never echoing back the input value
  object AsDetailedValidationError {
    def forPredicate[P](humanMsg: String): AsDetailedValidationError[P] =
      (sanitizedPrefix, technicalError) => {
        val paddedPrefix = if (sanitizedPrefix.isEmpty) "" else s" $sanitizedPrefix "
        ParseFailure(sanitized = paddedPrefix + humanMsg, details = technicalError)
      }
  }

  /** Custom version of refined refineV(), with 3 updates:
    *   - using our ToDetailedValidationErr to produce detailed error messages
    *   - optionally, a logical name can be specified to make the error more meaningful
    *   - is invoked with the target Refined[T, P], which I find more friendly to end-users than P
    */
  def refineDetailed[RTP] = new RefinedDetailedPartiallyApplied[RTP]

  class RefinedDetailedPartiallyApplied[RTP] {
    def apply[T, P](rawValue: T, varName: String)(implicit
        ev: RTP =:= Refined[T, P],
        v: Validate[T, P],
        asDetailedErr: AsDetailedValidationError[P]
    ): Either[ParseFailure, Refined[T, P]] = {
      refineV(rawValue).leftMap(technical => asDetailedErr(varName, technical))
    }
  }

  /** Same as refineDetailed(), but for an optional input, considering None as a valid absent value, yielding a
    * Right(None).
    */
  def refineOptDetailed[RTP] = new RefineOptDetailed[RTP]

  class RefineOptDetailed[RTP] {
    def apply[T, P](rawValue: Option[T], name: String)(implicit
        ev: RTP =:= Refined[T, P],
        v: Validate[T, P],
        asDetailedErr: AsDetailedValidationError[P]
    ): Either[ParseFailure, Option[Refined[T, P]]] =
      OptionT(rawValue.asRight[ParseFailure])
        .flatMapF(value => refineDetailed(value, name).map(_.some))
        .value
  }

  /** Essentially just a copy of ValidatingQueryParamDecoderMatcher, though relying on my own refineDetailed() handling
    * of refined type. We rely on some QueryParamDecoder for the unrefined T type (i.e decoding an int from a String,
    * not done here but delegated)
    */
  def namedQueryParamDecoderMatcher[T: QueryParamDecoder, P](
      paramName: String
  )(implicit ev: Validate[T, P], asDetailedErr: AsDetailedValidationError[P]) = new {
    def unapply(httpParams: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, T Refined P]] =
      httpParams
        .get(paramName)
        .flatMap(_.headOption)
        .map { paramValue =>
          QueryParamDecoder[T]
            .decode(QueryParameterValue(paramValue))
            .andThen(raw => refineDetailed[T Refined P](raw, s"Invalid query param: $paramName").toValidatedNel)
        }
  }

  // (keeping this around in case I change my mind again...)
  // implicit def refinedQueryParamDecoder[T: QueryParamDecoder, P](implicit
  //     ev: Validate[T, P],
  //     asDetailedErr: AsDetailedValidationError[P]
  // ): QueryParamDecoder[T Refined P] = QueryParamDecoder[T].emap(refineDetailed(_, ""))

  def validated[A](validated: ValidatedNel[ParseFailure, A]): Try[A] =
    validated match {
      case Invalid(failures) =>
        val mergedSanitized = failures.map(_.sanitized).toList.mkString(",").trim()
        val mergedDetails = failures.map(_.details).toList.mkString(",")
        Failure(ParseFailure(mergedSanitized, mergedDetails))
      case Valid(a) => Success(a)
    }
}

object core {

  import org.log4s._
  private[this] val logger = getLogger

  class TechnicalError(err: String) extends RuntimeException

  def makeId[A](implicit ev: Refined[String, Uuid] =:= A): Either[TechnicalError, A] =
    refineV[Uuid](java.util.UUID.randomUUID().toString())
      .fold(
        err => {
          logger.error("should never happened: generated an invalid UUID, this is a BUG :(")
          Left(new TechnicalError(err))
        },
        a => Right(a)
      )
}

object logging {

  import org.log4s._

  implicit class BookshelfIoOps[A](val ioa: IO[A]) extends AnyVal {
    def debug(implicit logger: Logger): IO[A] = ioa.map(a => { logger.info(a.toString()); a })
  }

}
