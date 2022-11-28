package bookshelf.clientdemo

import bookshelf.BookshelfServer
import bookshelf.BookshelfServerApp
import bookshelf.AppConfig
import bookshelf.catalog.Authors._
import bookshelf.catalog.Books
import bookshelf.catalog.Books._
import bookshelf.catalog.Categories._
import bookshelf.utils.debug._
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.flatMap._
import doobie._
import doobie.implicits._
import eu.timepit.refined._
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s.EntityEncoder
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers._
import org.http4s.implicits._

/** Demo of an HTTP client to the bookshelf app (requires the Bookshelf app to already be running)
  */
object ClientDemo extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    BookshelfClient
      .build(uri"http://localhost:8080/")
      .use(client =>
        for {
          _ <- resetDbTestData(BookshelfServerApp.localDockerConfig)
          _ <- client.getAuthor(refineMV("d9344e5b-1c16-420f-9824-1f03277b32fe")).debug
          _ <- client.getAllAuthors.debug
          // TODO: BUG: non existring category name leads to client failing to parse json
          _ <- client.getCategory(refineMV("novel")).debug
          _ <- client.getAllCategories.debug
          _ <- client.getBook(refineMV("442cda93-5117-412d-9c87-cf9cc73bfad7")).debug
          catId <- client.createCategory(CreateCategory(refineMV("travel"), refineMV("be somewhere else today"))).debug
          _ <- client.getCategory(refineMV("travel")).debug
          authorId <- client.createAuthor(CreateAuthor(refineMV("Chris"), refineMV("Pountney")))
          _ <- client.getAuthor(authorId).debug
          bookId <- client.createBook(
            CreateBook(
              refineMV("Into the Sunshine"),
              authorId,
              Some(refineMV(2018)),
              List(
                catId,
                refineMV("9d151f50-44ca-44f0-aec4-f06daf6b3659")
              ),
              Some(
                "In Part Two of Chris Pountney’s attempt to cycle around the world, a disaster in the Pacific Ocean soon forces a drastic change of plans."
              )
            )
          )
          _ <- client.getBook(bookId).debug
        } yield ExitCode.Success
      )
  }

  def resetDbTestData(config: AppConfig): IO[Unit] = {
    IO.println("Populating DB with test data") >>
      BookshelfServer
        .localHostTransactor(config)
        .use(xa => (deleteAll.run.transact(xa) >> insertTestData.run.transact(xa)).void) >>
      IO.println("DB now contains data")
  }

  val deleteAll = sql"""
    DELETE FROM book_category;
    DELETE FROM book;
    DELETE FROM category;
    DELETE FROM author;
  """.update

  val insertTestData = sql"""
    insert into author (id, first_name, last_name) values ('dda2fba4-5525-4fd3-9e84-b501aee0f6e5', 'Philip', 'Coggan');
    insert into author (id, first_name, last_name) values ('d9344e5b-1c16-420f-9824-1f03277b32fe', 'Adam', 'Hochschild');
    insert into author (id, first_name, last_name) values ('07a84aed-d860-4e6e-879a-4a568d6acdf7', 'Hans', 'Rosling');
    insert into author (id, first_name, last_name) values ('eb7fdd9f-68a6-491d-9a1b-dace2816f0d5', 'Yuval Noah', 'Harari');

    insert into category (id, name, description) values ('9d151f50-44ca-44f0-aec4-f06daf6b3659', 'novel', 'story stuff');

    insert into category (id, name, description) values ('bf89fcad-fd4c-4da2-a3b9-7290442cc079', 'non-fiction', 'for learning stuff');
    insert into category (id, name, description) values ('14feaefd-c7b7-4e78-8d40-221aceca2bb4', 'comic-book', 'with pictures');
    insert into category (id, name, description) values ('b1600876-6fcf-4c0b-9c6f-fed034eb9f16', 'self-help', 'solve your problem');
    insert into category (id, name, description) values ('796f75d1-5860-422d-8bfe-c95231e0f7f3', 'history', 'happened in the past');

    insert into book (id, title, author_id, publication_year, summary) 
        values ('442cda93-5117-412d-9c87-cf9cc73bfad7', 'More', 'dda2fba4-5525-4fd3-9e84-b501aee0f6e5', 2020, 
        'More tracks the development of the world economy, starting with the first obsidian blades that made their way from what is now Turkey to the Iran-Iraq border 7000 years before Christ, and ending with the Sino-American trade war that we are in right now.');
    insert into book (id, title, author_id, publication_year, summary) 
        values ('3f02ac45-05ae-4b38-a1e4-022ddc2d0666', 'King Leopold''s Ghost', 'd9344e5b-1c16-420f-9824-1f03277b32fe', 1999, 
        'In the 1880s, as the European powers were carving up Africa, King Leopold II of Belgium seized for himself the vast and mostly unexplored territory surrounding the Congo River.');
    insert into book (id, title, author_id, publication_year, summary) 
        values ('c0f7cea6-0798-4955-9233-e642307f53f3', 'Factfulness', '07a84aed-d860-4e6e-879a-4a568d6acdf7', 2018, 
        'When asked simple questions about global trends—what percentage of the world’s population live in poverty; why the world’s population is increasing; how many girls finish school—we systematically get the answers wrong.');
    insert into book (id, title, author_id, publication_year, summary) 
        values ('c7b27f1b-9585-4c2d-b7ce-c1e0af73421f', 'Sapiens', 'eb7fdd9f-68a6-491d-9a1b-dace2816f0d5', 2015, 
        'How did our species succeed in the battle for dominance? Why did our foraging ancestors come together to create cities and kingdoms?');

    insert into book_category (book_id, category_id) values ('442cda93-5117-412d-9c87-cf9cc73bfad7', 'bf89fcad-fd4c-4da2-a3b9-7290442cc079');
    insert into book_category (book_id, category_id) values ('442cda93-5117-412d-9c87-cf9cc73bfad7', '796f75d1-5860-422d-8bfe-c95231e0f7f3');
    insert into book_category (book_id, category_id) values ('3f02ac45-05ae-4b38-a1e4-022ddc2d0666', 'bf89fcad-fd4c-4da2-a3b9-7290442cc079');
    insert into book_category (book_id, category_id) values ('3f02ac45-05ae-4b38-a1e4-022ddc2d0666', '796f75d1-5860-422d-8bfe-c95231e0f7f3');
    insert into book_category (book_id, category_id) values ('c0f7cea6-0798-4955-9233-e642307f53f3', '796f75d1-5860-422d-8bfe-c95231e0f7f3');

    ANALYZE;

  """.update

}
