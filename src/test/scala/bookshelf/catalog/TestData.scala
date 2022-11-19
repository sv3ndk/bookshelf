package bookshelf.catalog

import bookshelf.util.effect
import cats.effect.IO
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uuid
import org.scalacheck.Gen

object TestData {
  def mockCategoriesService(data: Map[Categories.CategoryName, Categories.Category]): IO[Categories[IO]] =
    effect.EffectMap
      .make[IO, Categories.CategoryName, Categories.Category](data)
      .map { state =>
        new Categories[IO] {
          def getAll = state.getAllValues
          def get(name: Categories.CategoryName) = state.get(name)
          def add(category: Categories.Category) = state.add(category.name, category)
        }
      }

  def mockBooksService(data: Map[Books.BookId, Books.Book]): IO[Books[IO]] =
    effect.EffectMap
      .make[IO, Books.BookId, Books.Book](data)
      .map { state =>
        new Books[IO] {
          def add(book: Books.Book) = state.add(book.id, book)
          def get(id: Books.BookId) = state.get(id)
        }
      }

  val nonEmptyAlphaNumString: Gen[String] = for {
    first <- Gen.alphaNumChar
    rest <- Gen.alphaNumStr
  } yield s"$first$rest"

  def refinedGen[T, P](source: Gen[T])(implicit ev: Validate[T, P]): Gen[T Refined P] =
    source.map(t => Refined.unsafeApply(t))

  val genNonEmpytString: Gen[String Refined NonEmpty] = refinedGen(nonEmptyAlphaNumString)
  val genUuid: Gen[String Refined Uuid] = refinedGen(Gen.uuid.map(_.toString()))
  val genUuidList: Gen[List[String Refined Uuid]] = Gen.listOf(genUuid)
  val publicationYearGen: Gen[Books.BookPublicationYear] = refinedGen(Gen.choose(1801, 2199))

  val fineCategoryGen: Gen[Categories.Category] = for {
    id <- genUuid
    name <- genNonEmpytString
    description <- genNonEmpytString
  } yield Categories.Category(id, name, description)

  val fineBookGen: Gen[Books.Book] = for {
    id <- genUuid
    title <- genNonEmpytString
    authorId <- genUuid
    year <- publicationYearGen
    categories <- genUuidList
    summary <- nonEmptyAlphaNumString
  } yield Books.Book(id, title, authorId, year, categories, summary)

}
