CREATE TABLE IF NOT EXISTS category (
    id varchar(36) NOT NULL,
    name varchar(25) NOT NULL,
    description text
);


ALTER TABLE category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);

-- ALTER TABLE country
--     ADD CONSTRAINT country_capital_fkey FOREIGN KEY (capital) REFERENCES city(id);


-- some test data

insert into category (id, name, description) values ('9d151f50-44ca-44f0-aec4-f06daf6b3659', 'novel', 'story stuff');
insert into category (id, name, description) values ('bf89fcad-fd4c-4da2-a3b9-7290442cc079', 'non-fiction', 'for learning stuff');
insert into category (id, name, description) values ('14feaefd-c7b7-4e78-8d40-221aceca2bb4', 'comic-book', 'with pictures');
insert into category (id, name, description) values ('b1600876-6fcf-4c0b-9c6f-fed034eb9f16', 'self-help', 'solve your problem');

ANALYZE;

