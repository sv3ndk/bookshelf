# Bookshelf

Toy application to handle books and bookshelves, as an excuse to play with Typelevel Cats eco-system.



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

services:

  * catalog: handling of the book catalog available on the site, including search
  * shelve: allow users to manage their bookshelves
  * session: login, logout
  * social: comments, rating, moderation