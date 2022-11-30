# Bookshelf

Toy application to handle books and bookshelves, as an excuse to play with the Cats eco-system.

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

# Design:

## code decisions

* most operations are super simple CRUD stuff => 3 simple layers:
    *  `Http`: responsible for web routing, (de)serialization...
    * `Services`: business scenario (when they exist), definition of dB transaction boundaries, creation of entity id, retries and fall-back strategy
    * `Persistence`: interraction with DB
* Error handling:
  * In case of invalid input, the `Http` layers attempts to provide helpful feed-back, without echoing the input
  * business errors are propagated as a `Left` of some error ADT and explicitly handled in the `Http` layer
  * Technical errors should "bubble up" in the `IO` error channel and yield an HTTP 500
* I'm not using tagless final but rather committing to the concrete `IO` effect.  
* I followed the mantra "package together things that change together", s.t. things are grouped in folder per domain (`catalog`, `session`,..) 
* Integration tests rely on docker-compose env, strated automatically

## domains
  * `catalog`: handling of the book catalog available on the site, including search
  * `shelf`: allow users to manage their bookshelves (TODO)
  * `profile`: login, logout, user creation (TODO)
  * `social`: comments, rating, moderation (TODO)

## Tech stack:

* core: cats, cats-effect, log4s
* web layer: http4s, circe
* data validation: refined
* persistence: doobie, postgreSQL
* tests: munit, scalacheck, testcontainers-scala, docker

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


# Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* Doobie `check()` is quite good at detecting compatibility of the SQL queries with the scala types

* stack traces in app written with Cats are pretty much unusable since they often show mostly plumbing technical info as opposed to pointing to the source of the issue. That's because my code is not what run directy in prod: my code generates a program that runs in prod, and the stack trace is expressed in terms of _that_ program, not my original code.

* I don't like over-user of implicits, they fail in obscure ways when the relevant imports are missing, especially when a chain of them is necessary (e.g. a function requiring an implicit entity decorer, itself requiring a json decoder, itself requiring a refined type decoder...), as a user I need to understand lot's of implementation details to fix my bugs when I used them incorrectly.

* My current feeling towards tagless final is that it's probably overly-generalization, adding an additional layer of abstraction for little added value. OTOH without it we end up with all functions returning `IO[Stuff]` which is a bit the effect equivalent of returning `Object` everywhere, it's super broad. Ideally, we should seek opportunities to express business logic as simple functions outside of any effect, and delegate to it from and effectful layer using `IO.fromEither(...)` or so.

* Call me impure all you want, though I much prefer using log4s to log4cats: do we _really_ need to capture logs as effect? Also, log4cats prevents to log from pure functions, since we need to express logging in an effect, which is at odds with my current feeling that we should express as much logic as possible in pure functions, outside of effect. 


TODO: 

* authentication: add JWT
  * update http client to add JWT token with some hard-coded key 
  * update middleware to retrieve user id from JWT => do my integration tests still work? + update demo client

* retry strategy
* shelf domain + add a Redis persistence