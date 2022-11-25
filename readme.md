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



# Design:


## KISS code structure

* most operations are super simple CRUD stuff => 3 simple layers:
    * http: responsible for web routing, (de)serialization, authentication...
    * persistence: interraction with DB
    * services: business scenario (when they exist), definition of dB transaction boundaries, retries, fall-back strategy
* I'm not using tagless final but rather committing to the concrete IO effect
* the transaction boundary is defined in the application entrypoint, i.e. the http layer. It's a bit unclean since it makes the http layer
  in charge of a persistence concern, but it keeps things simple. As the application grows, we could move that concern to a business layer
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
* persistence: Doobie and postgres
* unit-tests: munit and scalacheck



# Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* error handling in http4s can either be specifically in each route, or else for repetitive stuff we can let the `MessageFailure` "bubbleUp" and use a middleware to transform it. I crafted a middleware that returns quite a verbose message, it's probably not the most secure option.

* I don't like implicits, they fail in obscure ways when the relevant imports are missing, especially when a chain of them is necessary (e.g. a functio requireing an implicit entity decorer, itself requiring a json decoder, itself requiring a refined type decoder...), as a user I need to understand lot's of implementation details to fix my bugs when I used them incorrectly.

* stack traces in app written with Cats are pretty much unusable since they often show mostly pluming technical info as opposed to pointing to the source of the issue 

* Tagless final is probably overly-generalization that adds an additional layer of abstraction for little added value. OTOH we end up with all functions returning `IO[Stuff]` which is a bit the effect equivalent of returning `Object` everywhere, it's super broad. Ideally, we should seek opportunities to express logic as simple functions outside of any effect, and delegate to it from and effectful layer.

Further notes:

* 4 downsides to my approach for custom error messages: 
    * it's based on implicits
    * I'm returning 400 BadRequest for any error
    * it's prone to overlap: once an error message is associated to a constraint for one meaning (say, a publication year should be > 1800), we cannot define another one error for the same constrain in another context (say, a page count that shoudl also be > 1800 for some reason)
    * it doesn't address semantic meaning: 2 types that are equivalent at type level with different semantics (say AuthorId and BookId both being `String Refined Uuid`) can still be passed one instead of the other


TODO:
* add persistence layer:
  * fix missign category when saving a book
  * fix current UTs + add a few
  * convert the demo client to an integration test, using testcontainer scala https://github.com/testcontainers/testcontainers-scala
  * improve error handling: empty list in json input, non-existing author id when creating a book,...
  * add ability to update and delete
  * add calls to Doobie query check() as part of integration test

* add pagination to book and author queries
* logging
* retry strategy
* authentication + profile domain
* shelf domain + add a Redis persistence
* 