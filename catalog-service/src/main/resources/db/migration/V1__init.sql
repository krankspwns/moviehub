CREATE TABLE movies (
    id                    VARCHAR(36)   PRIMARY KEY,
    title                 VARCHAR(200)  NOT NULL,
    synopsis              VARCHAR(4000) NOT NULL DEFAULT '',
    release_year          INT           NOT NULL,
    runtime_minutes       INT           NOT NULL DEFAULT 0,
    artwork_content_type  VARCHAR(100),
    artwork_version       BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMP(6)  NOT NULL,
    updated_at            TIMESTAMP(6)  NOT NULL
);

CREATE INDEX idx_movies_title ON movies (title);

CREATE TABLE movie_genres (
    movie_id  VARCHAR(36) NOT NULL,
    genre     VARCHAR(50) NOT NULL,
    CONSTRAINT fk_movie_genres_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

CREATE INDEX idx_movie_genres_movie ON movie_genres (movie_id);

CREATE TABLE people (
    id          VARCHAR(36)   PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    bio         VARCHAR(4000) NOT NULL DEFAULT '',
    birth_date  DATE,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL
);

CREATE INDEX idx_people_name ON people (name);

CREATE TABLE credits (
    id              VARCHAR(36)  PRIMARY KEY,
    movie_id        VARCHAR(36)  NOT NULL,
    person_id       VARCHAR(36)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    character_name  VARCHAR(200) NOT NULL DEFAULT '',
    CONSTRAINT fk_credits_movie  FOREIGN KEY (movie_id)  REFERENCES movies (id) ON DELETE CASCADE,
    CONSTRAINT fk_credits_person FOREIGN KEY (person_id) REFERENCES people (id) ON DELETE CASCADE,
    CONSTRAINT uq_credits_movie_person_role UNIQUE (movie_id, person_id, role)
);

CREATE INDEX idx_credits_person ON credits (person_id);

