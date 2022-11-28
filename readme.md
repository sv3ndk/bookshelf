# Bookshelf

Toy application to handle books and bookshelves, as an excuse to play with Typelevel Cats eco-system.

# Use cases:

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


# How to run

## Tests:

Unit tests:
```scala
// from sbt shell
test
```

Integration tests:
```scala
// from sbt shell
IntegrationTest/test
```

## Demo client app:

Start test environment:
```shell
docker-compose  -f ./src/it/resources/docker-compose.yml up
```

Then launch the app and the demo:
```scala
// start the app from one sbt shell:
runMain bookshelf.BookshelfServerApp

// start the demo from another sbt shell:
runMain bookshelf.clientdemo.ClientDemo
```


# Design:


## code structure

* most operations are super simple CRUD stuff => 3 simple layers:
    *  `Http`: responsible for web routing, (de)serialization, authentication...
    * `Services`: business scenario (when they exist), definition of dB transaction boundaries, creation of entity id, retries and fall-back strategy
    * `Persistence`: interraction with DB
* I'm not using tagless final but rather committing to the concrete IO effect
* I followed the mantra "package together things that change together", s.t. things are grouped in folder per domain (`catalog`, `session`,..) 
  instead of grouping them by technical concern (e.g. `model`, `http`, `persistence`,...)

## domains
  * `catalog`: handling of the book catalog available on the site, including search
  * `shelf`: allow users to manage their bookshelves
  * `profile`: login, logout, user creation
  * `social`: comments, rating, moderation

## Tech stack:

* core: cats and cats-effect
* rest layer: http4s
* data validation: refined
* persistence: doobie, postgreSQL
* tests: munit, scalacheck, testcontainers-scala

# Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* error handling in http4s can either be specifically in each route, or else for repetitive stuff we can let the `MessageFailure` "bubbleUp" and use a middleware to transform it. I crafted a middleware that returns quite a verbose message, it's probably not the most secure option.

* Doobie check() is quite good at detecting compatibility of the query with the scala types

* I don't like implicits, they fail in obscure ways when the relevant imports are missing, especially when a chain of them is necessary (e.g. a functio requireing an implicit entity decorer, itself requiring a json decoder, itself requiring a refined type decoder...), as a user I need to understand lot's of implementation details to fix my bugs when I used them incorrectly.

* stack traces in app written with Cats are pretty much unusable since they often show mostly pluming technical info as opposed to pointing to the source of the issue 

* My current feeling towards tagless final is that it's probably overly-generalization, adding an additional layer of abstraction for little added value. OTOH without it we end up with all functions returning `IO[Stuff]` which is a bit the effect equivalent of returning `Object` everywhere, it's super broad. Ideally, we should seek opportunities to express business logic as simple functions outside of any effect, and delegate to it from and effectful layer using `IO.fromEither(...)` or so.

Further notes:

* 4 downsides to my approach for custom error messages: 
    * it's based on implicits
    * I'm returning 400 BadRequest for any error
    * it's prone to overlap: once an error message is associated to a constraint for one meaning (say, a publication year should be > 1800), we cannot define another one error for the same constrain in another context (say, a page count that shoudl also be > 1800 for some reason)
    * it doesn't address semantic meaning: 2 types that are equivalent at type level with different semantics (say AuthorId and BookId both being `String Refined Uuid`) can still be passed one instead of the other


TODO:
* add persistence layer:
  * finish integration tests: add more scenarios 
  * improve error handling: empty list in json input, non-existing author id when creating a book,...
  * add ability to update and delete
  * add pagination to book and author queries


* logging
* retry strategy
* authentication + profile domain
* shelf domain + add a Redis persistence
* 