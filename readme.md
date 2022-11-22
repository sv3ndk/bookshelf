# Bookshelf

Toy application to handle books and bookshelves, as an excuse to play with Typelevel Cats eco-system.

Tech stack:

* core: cats and cats-effect
* rest layer: http4s
* data validation: refined
* persistence: Doobie and postgres
* unit-tests: munit and scalacheck


Use cases:

* moderators can
  * add/update/archive books 
  * optionally, a summary could automatically be fetched online for that book and suggest during book creation
  * create/rename/archive genre 
  * assign a category to a book: a category is assigned to a book globally (for all users)
  * validate book creation/update suggestion
  * validate moderation feed-back

* anonymous users can
  * discover existing books + comments + ratings
      * search by title LIKE
      * list by genre

* users can:
  * suggest book creation or update
  * organize their books in bookshelves:
    * create/rename/delete a bookshelf
    * add/remove books to bookshelf
    * list all their books in a bookshelf
  * list all their books across any bookshelf
  * add comments to book their books
  * create tags and assign them to books: as opposed to category, a tag exists only for one given user
  * flag comments as problematic
  * add ratings to books

domains:
  * catalog: handling of the book catalog available on the site, including search
  * shelve: allow users to manage their bookshelves
  * session: login, logout
  * social: comments, rating, moderation



# Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* error handling in http4s can either be specifically in each route, or else for repetitive stuff we can let the `MessageFailure` "bubbleUp" and use a middleware to transform it. I crafted a middleware that returns quite a verbose message, it's probably not the most secure option.

* I don't like implicits, they fail in obscure ways when the relevant imports are missing

* stack traces in an app written with Cats are pretty much unusable since they often show mostly pluming technical info as opposed to pointing to the source of the issue 

Further notes:

* 4 downsides to my approach for custom error messages: 
    * it's based on implicits
    * I'm returning 400 BadRequest for any error
    * it's prone to overlap: once an error message is associated to a constraint for one meaning (say, a publication year should be > 1800), we cannot define another one error for the same constrain in another context (say, a page count that shoudl also be > 1800 for some reason)
    * it doesn't address semantic meaning: 2 types that are equivalent at type level with different semantics (say AuthorId and BookId both being `String Refined Uuid`) can still be passed one instead of the other

# Experimenting with the DB

```shell
# launches local DB + populates with test data
docker-compose up
```

From sbt console:

```scala
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.Stream

import cats.effect.unsafe.implicits.global

val xa = Transactor.fromDriverManager[IO](
  "org.postgresql.Driver",     
  "jdbc:postgresql:bookshelf",     
  "testuser",                  
  "testpassword"                          
)

// this works 
val rawCat = bookshelf.catalog.CatalogRoutes.RawCategory("3092fc51-dc11-4ab3-a2ec-7df03a81aa73", "testfoo", "bla")
sql"""
  insert into category (id, name, description)
  values (${rawCat.id}, ${rawCat.name}, ${rawCat.description})
  """.update.run
  .transact(xa)     
  .unsafeRunSync()  

// this works
sql"select id, name, description from category"
  .query[(String, String, String)]    
  .to[List]         
  .transact(xa)     
  .unsafeRunSync()  
  .foreach(l => println(l)) 

// this requires to be executed from an instance inheriting from doobie.refined.Instances
// SecondaryValidationFailed
sql"select id, name, description from category"
  .query[bookshelf.catalog.Categories.Category]    
  .to[List]         
  .transact(xa)     
  .unsafeRunSync()  
  .foreach(l => println(l)) 

```


