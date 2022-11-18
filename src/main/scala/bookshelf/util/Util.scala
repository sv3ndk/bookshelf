package bookshelf.util

import cats.MonadThrow
import cats.data.ValidatedNel
import cats.effect.Ref
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.functor._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection._
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
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

object refined {

  // some common human-friendly validation errors
  implicit val NonEmptyErr = ToDetailedValidationErr.forRefined[NonEmpty]("should not be empty")

  case class DetailedValidationErr[P](technicalMsg: String, humanMsg: String)

  // provider of a detailed error for predicate P given some String technical error
  type ToDetailedValidationErr[P] = String => DetailedValidationErr[P]

  object ToDetailedValidationErr {
    def forRefined[P](humanMsg: String): ToDetailedValidationErr[P] =
      (technical) => DetailedValidationErr(technical, humanMsg)
  }

  // custom version of refined refineV(), using our converter to add details to the validation error
  def refineVDetailed[T, P](t: T)(implicit
      v: Validate[T, P],
      toDetailedErr: ToDetailedValidationErr[P]
  ): Either[DetailedValidationErr[P], Refined[T, P]] =
    refineV(t).leftMap(technical => toDetailedErr(technical))

  // derived http4s QueryParamDecoder[Refined[T, P]] based on our custom validation
  implicit def refinedQueryParamDecoder[T: QueryParamDecoder, P](implicit
      ev: Validate[T, P],
      toDetailedErr: ToDetailedValidationErr[P]
  ): QueryParamDecoder[T Refined P] = QueryParamDecoder[T]
    .emap(refineVDetailed(_).leftMap {
      case DetailedValidationErr(technical, friendly) => { ParseFailure(friendly, technical) }
    })

  // custom version of the ValidatingQueryParamDecoderMatcher which add the param name to the error
  abstract class NamedValidatingQueryParamDecoderMatcher[T: QueryParamDecoder](name: String) {

    object delegate extends ValidatingQueryParamDecoderMatcher[T](name)

    def unapply(params: Map[String, Seq[String]]): Option[ValidatedNel[ParseFailure, T]] =
      delegate
        .unapply(params)
        .map(
          _.leftMap(failureList =>
            failureList.map { case ParseFailure(sanitized, details) =>
              // TOTO: we might add the original value into the detailed error here
              ParseFailure(s"Invalid query param '$name': $sanitized", s"Invalid query param \"$name\": $details")
            }
          )
        )
  }

}
