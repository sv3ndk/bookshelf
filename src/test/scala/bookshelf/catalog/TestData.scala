package bookshelf.catalog

import bookshelf.utils.effect
import cats.effect.IO
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uuid
import org.scalacheck.Gen
import eu.timepit.refined._

object TestData {
  def mockCategoriesService(data: Map[Categories.CategoryName, Categories.Category]): IO[Categories] =
    effect.EffectMap
      .make[IO, Categories.CategoryName, Categories.Category](data)
      .map { state =>
        new Categories {
          def getAll = state.getAllValues
          def get(name: Categories.CategoryName) = state.get(name)
          def add(category: Categories.Category) = state.add(category.name, category)
        }
      }

  def mockBooksService(data: Map[Books.BookId, Books.Book]): IO[Books] =
    effect.EffectMap
      .make[IO, Books.BookId, Books.Book](data)
      .map { state =>
        new Books {
          // def add(book: Books.Book) = state.add(book.id, book)
          def get(id: Books.BookId) = state.get(id)
        }
      }

  val nonEmptyAlphaNumString: Gen[String] = for {
    first <- Gen.alphaNumChar
    rest <- Gen.alphaNumStr
  } yield s"$first$rest"

  // force a refinement into P, or blows up (which would be a bug of the caller)
  def refinedGen[T, P](source: Gen[T])(implicit ev: Validate[T, P]): Gen[T Refined P] =
    source.map(t => refineV[P](t).toOption.get)

  def fakeDb[ID, T](genT: Gen[T])(id: T => ID): Gen[Map[ID, T]] =
    Gen.nonEmptyListOf(genT.map(t => id(t) -> t)).map(_.toMap)

  import Categories._

  val genNonEmpytString: Gen[String Refined NonEmpty] = refinedGen(nonEmptyAlphaNumString)
  val genUuid: Gen[String Refined Uuid] = refinedGen(Gen.uuid.map(_.toString()))
  val genUuidList: Gen[List[String Refined Uuid]] = Gen.listOf(genUuid)
  val nameGen: Gen[Categories.CategoryName] =
    refinedGen(
      Gen
        .choose(1, 25)
        .flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))
    )

  val publicationYearGen: Gen[Books.BookPublicationYear] = refinedGen(Gen.choose(1801, 2199))

  val fineCategoryGen: Gen[Categories.Category] = for {
    id <- genUuid
    name <- nameGen
    description <- genNonEmpytString
  } yield Categories.Category(id, name, description)

  val fineCategoriesGen: Gen[Map[Categories.CategoryName, Categories.Category]] = fakeDb(fineCategoryGen)(_.name)

  val fineBookGen: Gen[Books.Book] = for {
    id <- genUuid
    title <- genNonEmpytString
    authorId <- genUuid
    year <- publicationYearGen
    categories <- genUuidList
    summary <- nonEmptyAlphaNumString
  } yield Books.Book(id, title, authorId, year, categories, summary)

  val fineBooksGen: Gen[Map[Books.BookId, Books.Book]] = fakeDb(fineBookGen)(_.id)

}
