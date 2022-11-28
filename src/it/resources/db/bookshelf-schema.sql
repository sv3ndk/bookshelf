-- Author
CREATE TABLE IF NOT EXISTS author (
    id varchar(36) NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL
);
ALTER TABLE author ADD CONSTRAINT author_pkey PRIMARY KEY (id);

-- Book category
CREATE TABLE IF NOT EXISTS category (
    id varchar(36) NOT NULL,
    name varchar(25) NOT NULL UNIQUE,
    description text
);
ALTER TABLE category ADD CONSTRAINT category_pkey PRIMARY KEY (id);


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
