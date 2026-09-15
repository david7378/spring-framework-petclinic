CREATE TABLE IF NOT EXISTS vets (
  id SERIAL,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  CONSTRAINT pk_vets PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_vets_last_name ON vets (last_name);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('vets_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM vets)), false);


CREATE TABLE IF NOT EXISTS specialties (
  id SERIAL,
  name VARCHAR(80),
  CONSTRAINT pk_specialties PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_specialties_name ON specialties (name);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('specialties_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM specialties)), false);


CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INT NOT NULL,
  specialty_id INT NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  CONSTRAINT unique_ids UNIQUE (vet_id,specialty_id)
);



CREATE TABLE IF NOT EXISTS types (
  id SERIAL,
  name VARCHAR(80),
  CONSTRAINT pk_types PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_types_name ON types (name);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('types_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM types)), false);

CREATE TABLE IF NOT EXISTS owners (
  id SERIAL,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20),
  CONSTRAINT pk_owners PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_owners_last_name ON owners (last_name);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('owners_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM owners)), false);


CREATE TABLE IF NOT EXISTS pets (
  id SERIAL,
  name VARCHAR(30),
  birth_date DATE,
  type_id INT NOT NULL,
  owner_id INT NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES owners(id),
  FOREIGN KEY (type_id) REFERENCES types(id),
  CONSTRAINT pk_pets PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_pets_name ON pets (name);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('pets_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM pets)), false);


CREATE TABLE IF NOT EXISTS visits (
  id SERIAL,
  pet_id INT NOT NULL,
  visit_date DATE,
  description VARCHAR(255),
  FOREIGN KEY (pet_id) REFERENCES pets(id),
  CONSTRAINT pk_visits PRIMARY KEY (id)
);

-- Idempotent: keeps 100 as the starting id on an empty table, but never rewinds below existing rows.
SELECT setval('visits_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 0) + 1 FROM visits)), false);
