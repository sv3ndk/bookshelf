package bookshelf.utils

import cats.MonadThrow
import cats.data.Kleisli
import cats.data.Validated
import cats.data.Validated.{Valid, Invalid}
import cats.data.ValidatedNel
import cats.effect.Ref
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

object effect {

  /** Map with an effectful API, handy for mocking some DB with an in-memory mock
    */
  trait EffectMap[F[_], K, V] {
    def getAll: F[List[(K, V)]]
    def getAllValues: F[List[V]]
    def get(key: K): F[Option[V]]
    def add(key: K, value: V): F[K]
    def remove(key: K): F[Unit]
  }

  object EffectMap {
    def make[F[_]: MonadThrow: Ref.Make, K, V](init: Map[K, V] = Map.empty[K, V]): F[EffectMap[F, K, V]] = {
      Ref
        .ofEffect(init.pure[F])
        .map { state =>
          new EffectMap[F, K, V] {
            def getAll: F[List[(K, V)]] = state.get.map(_.toList)
            def getAllValues: F[List[V]] = getAll.map(_.map(_._2))
            def get(key: K): F[Option[V]] = state.get.map(_.get(key))
            def add(key: K, value: V): F[K] = state.update(_ + (key -> value)).as(key)
            def remove(key: K): F[Unit] = state.update(_.removed(key))
          }
        }
    }
  }
}

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

  // Custom version of refined refineV(), with 3 updates:
  //  - using our ToDetailedValidationErr to produce detailed error messages
  //  - optionally, a logical name can be specified to make the error more meaningful
  //  - is invoked with the target Refined[T, P], which I find more friendly to end-users than P
  def refineVDetailed[RTP] = new RefinedPartiallyApplied[RTP]

  class RefinedPartiallyApplied[RTP] {
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

  // derived http4s QueryParamDecoder[Refined[T, P]] based on some existing QueryParamDecoder[T]
  // and our custom validation for refined types
  implicit def refinedQueryParamDecoder[T: QueryParamDecoder, P](implicit
      ev: Validate[T, P],
      asDetailedErr: AsDetailedValidationError[P]
  ): QueryParamDecoder[T Refined P] = QueryParamDecoder[T]
    .emap(refineVDetailed[T Refined P](_))

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

  def validated[F[_]: MonadThrow, A](validated: ValidatedNel[ParseFailure, A]): F[A] =
    validated match {
      case Invalid(e) =>
        val mergedSanitized = e.toList.map(_.sanitized).mkString(",").trim()
        val mergedDetails = e.toList.map(_.details).mkString(",")
        implicitly[MonadThrow[F]].raiseError(ParseFailure(mergedSanitized, mergedDetails))
      case Valid(genre) => genre.pure[F]
    }

}
