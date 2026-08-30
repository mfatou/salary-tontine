-- ---------------------------------------------------------------------------
-- SalaryTontine - V6
-- La tontine tourne à sa propre cadence, le salaire reste mensuel.
--
-- Une tontine peut désormais prélever chaque semaine, tous les dix jours, tous
-- les quinze jours ou chaque mois. L'unité du cycle n'est donc plus le mois
-- mais la période, numérotée de 1 à n (n = nombre de participants).
--
-- Le bulletin de salaire, lui, demeure mensuel : c'est la réalité de la paie.
-- Une tontine hebdomadaire produit quatre ou cinq retenues dans le même mois,
-- que le bulletin agrège. D'où la conservation de salary_month à côté de
-- period_index : l'un sert au cycle, l'autre à la paie.
-- ---------------------------------------------------------------------------

ALTER TABLE tontines
    ADD COLUMN frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE tontines
    ADD CONSTRAINT ck_tontines_frequency
        CHECK (frequency IN ('WEEKLY', 'TEN_DAYS', 'BIWEEKLY', 'MONTHLY'));

-- --------------------------------------------------------------- cotisations

ALTER TABLE contributions ADD COLUMN period_index INTEGER;
ALTER TABLE contributions ADD COLUMN period_start DATE;

-- Reprise des données existantes : toutes les tontines étaient mensuelles,
-- la période se déduit donc du nombre de mois écoulés depuis le départ.
UPDATE contributions c
SET period_start = to_date(c.contribution_month, 'YYYY-MM'),
    period_index = 1
        + (EXTRACT(YEAR FROM to_date(c.contribution_month, 'YYYY-MM'))::int * 12
           + EXTRACT(MONTH FROM to_date(c.contribution_month, 'YYYY-MM'))::int)
        - (EXTRACT(YEAR FROM t.start_date)::int * 12
           + EXTRACT(MONTH FROM t.start_date)::int)
FROM tontines t
WHERE t.id = c.tontine_id;

ALTER TABLE contributions ALTER COLUMN period_index SET NOT NULL;
ALTER TABLE contributions ALTER COLUMN period_start SET NOT NULL;

ALTER TABLE contributions DROP CONSTRAINT uk_contribution_tontine_user_month;
ALTER TABLE contributions DROP CONSTRAINT ck_contributions_month_format;
ALTER TABLE contributions DROP COLUMN contribution_month;

ALTER TABLE contributions
    ADD CONSTRAINT uk_contribution_tontine_user_period
        UNIQUE (tontine_id, user_id, period_index);

ALTER TABLE contributions
    ADD CONSTRAINT ck_contributions_period_index_min CHECK (period_index >= 1);

DROP INDEX IF EXISTS idx_contributions_tontine_month;
CREATE INDEX idx_contributions_tontine_period ON contributions (tontine_id, period_index);

-- ------------------------------------------------------------------ salaires

ALTER TABLE salary_records ADD COLUMN period_index INTEGER;
ALTER TABLE salary_records ADD COLUMN period_start DATE;

UPDATE salary_records s
SET period_start = to_date(s.salary_month, 'YYYY-MM'),
    period_index = 1
        + (EXTRACT(YEAR FROM to_date(s.salary_month, 'YYYY-MM'))::int * 12
           + EXTRACT(MONTH FROM to_date(s.salary_month, 'YYYY-MM'))::int)
        - (EXTRACT(YEAR FROM t.start_date)::int * 12
           + EXTRACT(MONTH FROM t.start_date)::int)
FROM tontines t
WHERE t.id = s.tontine_id;

ALTER TABLE salary_records ALTER COLUMN period_index SET NOT NULL;
ALTER TABLE salary_records ALTER COLUMN period_start SET NOT NULL;

-- Une tontine infra-mensuelle produit plusieurs lignes par mois : l'unicité
-- porte donc sur la période, et non plus sur le mois.
ALTER TABLE salary_records DROP CONSTRAINT uk_salary_record_user_tontine_month;

ALTER TABLE salary_records
    ADD CONSTRAINT uk_salary_record_user_tontine_period
        UNIQUE (user_id, tontine_id, period_index);

ALTER TABLE salary_records
    ADD CONSTRAINT ck_salary_records_period_index_min CHECK (period_index >= 1);

CREATE INDEX idx_salary_records_tontine_period ON salary_records (tontine_id, period_index);
