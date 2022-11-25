-- Author

CREATE TABLE IF NOT EXISTS author (
    id varchar(36) NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL
);
ALTER TABLE author ADD CONSTRAINT author_pkey PRIMARY KEY (id);

insert into author (id, first_name, last_name) values ('dda2fba4-5525-4fd3-9e84-b501aee0f6e5', 'Philip', 'Coggan');
insert into author (id, first_name, last_name) values ('d9344e5b-1c16-420f-9824-1f03277b32fe', 'Adam', 'Hochschild');
insert into author (id, first_name, last_name) values ('07a84aed-d860-4e6e-879a-4a568d6acdf7', 'Hans', 'Rosling');
insert into author (id, first_name, last_name) values ('eb7fdd9f-68a6-491d-9a1b-dace2816f0d5', 'Yuval Noah', 'Harari');


-- Book category
CREATE TABLE IF NOT EXISTS category (
    id varchar(36) NOT NULL,
    name varchar(25) NOT NULL,
    description text
);
ALTER TABLE category ADD CONSTRAINT category_pkey PRIMARY KEY (id);

insert into category (id, name, description) values ('9d151f50-44ca-44f0-aec4-f06daf6b3659', 'novel', 'story stuff');
insert into category (id, name, description) values ('bf89fcad-fd4c-4da2-a3b9-7290442cc079', 'non-fiction', 'for learning stuff');
insert into category (id, name, description) values ('14feaefd-c7b7-4e78-8d40-221aceca2bb4', 'comic-book', 'with pictures');
insert into category (id, name, description) values ('b1600876-6fcf-4c0b-9c6f-fed034eb9f16', 'self-help', 'solve your problem');
insert into category (id, name, description) values ('796f75d1-5860-422d-8bfe-c95231e0f7f3', 'history', 'happened in the past');


-- Books
CREATE TABLE IF NOT EXISTS book (
    id varchar(36) NOT NULL,
    title varchar NOT NULL,
    author_id varchar(36),
    publication_year int,
    summary text
);
ALTER TABLE book ADD CONSTRAINT book_pkey PRIMARY KEY (id);
ALTER TABLE book ADD CONSTRAINT book_author_fkey FOREIGN KEY (author_id) REFERENCES author(id);

CREATE TABLE IF NOT EXISTS book_category (
    book_id varchar(36) NOT NULL,
    category_id varchar(36) NOT NULL
);
ALTER TABLE book_category ADD CONSTRAINT book_category_book_id_fkey FOREIGN KEY (book_id) REFERENCES book(id);
ALTER TABLE book_category ADD CONSTRAINT book_category_category_id_fkey FOREIGN KEY (category_id) REFERENCES category(id);

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

