package bookshelf.utils

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.functor._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection._
import eu.timepit.refined.string._
import org.http4s.EntityEncoder
import org.http4s.HttpVersion
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MessageFailure
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.QueryParameterValue
import org.http4s.Request
import org.http4s.Response
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import _root_.cats.effect.IO

object validation {

  object CommonErrorMessages {
    implicit val InvalidUuid = AsDetailedValidationError.forPredicate[Uuid]("is not a valid UUID")
    implicit val UnexpectedlyEmpty = AsDetailedValidationError.forPredicate[NonEmpty]("should not be empty")
  }

  // provider of a detailed error for predicate P given some String technical error
  trait AsDetailedValidationError[P] {
    def apply(paramName: String, technicalMsg: String): ParseFailure
  }

  // Decorates a Refined Predicate P with a human-friendly error description, never echoing back the input value
  object AsDetailedValidationError {
    def forPredicate[P](humanMsg: String): AsDetailedValidationError[P] =
      (paramName, technicalError) => {
        val prefix = if (paramName.isEmpty) "" else s" $paramName "
        ParseFailure(sanitized = prefix + humanMsg, details = technicalError)
      }
  }

  /** Custom version of refined refineV(), with 3 updates:
    *   - using our ToDetailedValidationErr to produce detailed error messages
    *   - optionally, a logical name can be specified to make the error more meaningful
    *   - is invoked with the target Refined[T, P], which I find more friendly to end-users than P
    */
  def refineDetailed[RTP] = new RefinedDetailedPartiallyApplied[RTP]

  class RefinedDetailedPartiallyApplied[RTP] {
    def apply[T, P](
        rawValue: T,
        name: String = ""
    )(implicit
        ev: RTP =:= Refined[T, P],
        v: Validate[T, P],
        asDetailedErr: AsDetailedValidationError[P]
    ): Either[ParseFailure, Refined[T, P]] = {
      refineV(rawValue).leftMap(technical => asDetailedErr(name, technical))
    }
  }

  /** Same as refineDetailed(), but for an optional input, considering None as a valid non-present value, yielding a
    * Right(None).
    *
    * (I tried to express this with OptionT and Either, though since None is "good", I can't find an elegant way to make
    * it work ).
    */
  def refineOptDetailed[RTP] = new RefineOptDetailed[RTP]

  class RefineOptDetailed[RTP] {
    def apply[T, P](
        rawValue: Option[T],
        name: String = ""
    )(implicit
        ev: RTP =:= Refined[T, P],
        v: Validate[T, P],
        asDetailedErr: AsDetailedValidationError[P]
    ): Either[ParseFailure, Option[Refined[T, P]]] = {
      rawValue match {
        case None => Right(None)
        case Some(value) =>
          refineDetailed(value, name).map(Some(_))
      }
    }
  }

  // derived http4s QueryParamDecoder[Refined[T, P]] based on some existing QueryParamDecoder[T]
  // and our custom validation for refined types
  implicit def refinedQueryParamDecoder[T: QueryParamDecoder, P](implicit
      ev: Validate[T, P],
      asDetailedErr: AsDetailedValidationError[P]
  ): QueryParamDecoder[T Refined P] = QueryParamDecoder[T].emap(refineDetailed(_))

  // essentially just a copy of ValidatingQueryParamDecoderMatcher which adds the param name to the error
  // QP is typially a Refined[T, P], although this matcher is agnostic of that
  abstract class NamedQueryParamDecoderMatcher[QP: QueryParamDecoder](paramName: String) {
    def unapply(httpParams: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, QP]] =
      httpParams
        .get(paramName)
        .flatMap(_.headOption)
        .map { paramValue =>
          QueryParamDecoder[QP]
            .decode(QueryParameterValue(paramValue))
            .leftMap(failureList =>
              failureList.map { parseFailure =>
                parseFailure.copy(sanitized = s"Invalid query param '$paramName': ${parseFailure.sanitized}")
              }
            )
        }
  }

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

  class TechnicalError(err: String) extends RuntimeException

  def makeId[A](implicit ev: Refined[String, Uuid] =:= A): Either[TechnicalError, A] =
    refineV[Uuid](java.util.UUID.randomUUID().toString())
      .fold(
        err => Left(new TechnicalError(err)),
        a => Right(a)
      )
}

object debug {

  implicit class BookshelfIoOps[A](val ioa: IO[A]) extends AnyVal {
    def debug: IO[A] = ioa.map(a => { println(s"$a\n"); a })
  }
}
