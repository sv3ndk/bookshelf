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



Lessons learnt:

* this combinasion of imports conjures up automatic json decoding/encoding from/to case class while using refined types

```scala
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.circe.CirceEntityCodec._
```

* error handling in http4s can either be specifically in each route, or else for repetitive stuff we can let the `MessageFailure` "bubbleUp" and use a middleware to transform it. I crafted a middleware that returns quite a verbose message, it's probably not the most secure option.

* I don't like implicits, they fail in obscure ways when the relevant import is missing

Further notes:

* 2 downsides to my approach for custom error messages: it's based on implicits, and it's prone to overlap: once an error message is associated to a constraint for one meaning (say, a publication year should be > 1800), we cannot define another one error for the same constrain in another context (say, a page count that shoudl also be > 1800 for some reason)