# Bookshelf

Toy application to handle books and bookshelves, as an excuse to play with Typelevel Cats eco-system.

Tech stack:

* core: cats and cats-effect
* rest API: http4s
* data validation: Frank Thomas refined types
* unit-tests: munit and scalacheck


Use cases:

* moderators can
  * add/update/archive books 
  * optionally, a summary could automatically be fetched online for that book and suggest during book creation
  * create/rename/archive genre 
  * assign genre to book
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
  * add comments to books
  * flag comments as problematic
  * add ratings to books

domains:
  * catalog: handling of the book catalog available on the site, including search
  * shelve: allow users to manage their bookshelves
  * session: login, logout
  * social: comments, rating, moderation



Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* error handling in http4s can either be specifically in each route, or else for repetitive stuff we can let the `MessageFailure` "bubbleUp" and use a middleware to transform it. I crafted a middleware that returns quite a verbose message, it's probably not the most secure option.