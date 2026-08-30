-- ---------------------------------------------------------------------------
-- SalaryTontine - V5
-- Cycle de vie d'un compte : inscription libre, puis validation.
--
-- L'employe choisit lui-meme son mot de passe a l'inscription ; l'administrateur
-- valide le compte et attribue le role, sans jamais connaitre le mot de passe.
-- Un administrateur qui choisirait le mot de passe d'un employe pourrait se
-- faire passer pour lui : la validation remplace donc la creation de comptes.
--
-- Les comptes deja presents sont consideres valides : le DEFAULT vaut ACTIVE
-- pendant l'ajout de colonne, puis bascule sur PENDING pour les suivants.
-- ---------------------------------------------------------------------------

ALTER TABLE users
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE users
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED'));

CREATE INDEX idx_users_status ON users (status);
