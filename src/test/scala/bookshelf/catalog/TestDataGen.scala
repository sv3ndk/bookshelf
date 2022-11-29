package bookshelf.catalog

import cats.effect.IO
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Uuid
import org.scalacheck.Gen
import eu.timepit.refined._

object TestDataGen {
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
  val categoryNameGen: Gen[Categories.CategoryName] =
    refinedGen(
      Gen
        .choose(1, 25)
        .flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))
    )

  val publicationYearGen: Gen[Books.BookPublicationYear] = refinedGen(Gen.choose(1801, 2199))

  val rawCreateCategoryGen: Gen[CatalogRoutes.RawCreateCategory] = for {
    name <- categoryNameGen
    description <- nonEmptyAlphaNumString
  } yield CatalogRoutes.RawCreateCategory(name.value, description)

  val fineCategoryGen: Gen[Categories.Category] = for {
    id <- genUuid
    name <- categoryNameGen
    description <- genNonEmpytString
  } yield Categories.Category(id, name, description)

  val fineCategoriesGen: Gen[List[Categories.Category]] = Gen.nonEmptyListOf(fineCategoryGen)

  val fineAuthorGen: Gen[Authors.Author] = for {
    id <- genUuid
    firstName <- genNonEmpytString
    lastName <- genNonEmpytString
  } yield Authors.Author(id, firstName, lastName)

  val rawCreateBookGen: Gen[CatalogRoutes.RawCreateBook] = for {
    title <- genNonEmpytString
    authorId <- genUuid
    year <- Gen.option(publicationYearGen)
    ids <- genUuidList
    summary <- Gen.option(nonEmptyAlphaNumString)
  } yield CatalogRoutes.RawCreateBook(title.value, authorId.value, year.map(_.value), ids.map(_.value), summary)

  val fineBookGen: Gen[Books.Book] = for {
    id <- genUuid
    title <- genNonEmpytString
    author <- fineAuthorGen
    year <- Gen.option(publicationYearGen)
    categories <- fineCategoriesGen
    summary <- Gen.option(nonEmptyAlphaNumString)
  } yield Books.Book(id, title, author, year, categories, summary)

  val fineBooksGen: Gen[List[Books.Book]] = Gen.nonEmptyListOf(fineBookGen)

}
