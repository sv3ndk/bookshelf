package bookshelf.util

import cats.MonadThrow
import cats.data.Kleisli
import cats.data.Validated
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

  // some common human-friendly validation errors
  implicit val NonEmptyErr = ToDetailedValidationErr.forRefined[NonEmpty]("should not be empty")

  case class DetailedValidationErr(technicalMsg: String, humanMsg: String)

  // provider of a detailed error for predicate P given some String technical error
  trait ToDetailedValidationErr[P] {
    def apply(paramName: String, technicalMsg: String): DetailedValidationErr
  }

  object ToDetailedValidationErr {
    def forRefined[P](humanMsg: String): ToDetailedValidationErr[P] =
      (maybeParameName, technical) => {
        val prefix = if (maybeParameName.isEmpty) "" else s" $maybeParameName "
        DetailedValidationErr(prefix + technical, prefix + humanMsg)
      }
  }

  // custom version of refined refineV(), using our converter to add details to the validation error
  def refineVDetailed[T, P](value: T, name: String = "")(implicit
      v: Validate[T, P],
      toDetailedErr: ToDetailedValidationErr[P]
  ): Either[DetailedValidationErr, Refined[T, P]] =
    refineV(value).leftMap(technical => toDetailedErr(name, technical))

  // derived http4s QueryParamDecoder[Refined[T, P]] based on our custom validation
  implicit def refinedQueryParamDecoder[T: QueryParamDecoder, P](implicit
      ev: Validate[T, P],
      toDetailedErr: ToDetailedValidationErr[P]
  ): QueryParamDecoder[T Refined P] = QueryParamDecoder[T]
    .emap(refineVDetailed(_).leftMap {
      case DetailedValidationErr(technical, friendly) => { ParseFailure(friendly, technical) }
    })

  // essentially just a copy of ValidatingQueryParamDecoderMatcher which adds the param name to the error
  abstract class NamedValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {
    def unapply(params: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, T]] =
      params.get(name).flatMap(_.headOption).map { s =>
        QueryParamDecoder[T]
          .decode(QueryParameterValue(s))
          .leftMap(failureList =>
            failureList.map { case ParseFailure(sanitized, details) =>
              ParseFailure(s"Invalid query param '$name': $sanitized", s"Invalid query param \"$name\": $details")
            }
          )
      }
  }

  def validBody[F[_]: MonadThrow, A](validated: ValidatedNel[DetailedValidationErr, A]): F[A] =
    validated match {
      case Validated.Invalid(e) =>
        val mergedMessage = "Invalid message body:" + e.toList.map(_.humanMsg).mkString(",")
        implicitly[MonadThrow[F]].raiseError(ParseFailure(mergedMessage, mergedMessage))
      case Validated.Valid(genre) => genre.pure[F]
    }

  def validParam[F[_]: MonadThrow, A](validated: ValidatedNel[ParseFailure, A]): F[A] =
    validated match {
      case Validated.Invalid(e) =>
        val mergedMessage = e.toList.map(_.sanitized).mkString(",")
        implicitly[MonadThrow[F]].raiseError(ParseFailure(mergedMessage, mergedMessage))
      case Validated.Valid(genre) => genre.pure[F]
    }
}
