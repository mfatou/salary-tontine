-- ---------------------------------------------------------------------------
-- SalaryTontine - V7
-- Cadence libre : la durée d'un tour peut être n'importe quel nombre de jours.
--
-- Les quatre cadences prédéfinies couvrent les usages courants ; CUSTOM ouvre
-- le reste, de la tontine quotidienne à la tontine trimestrielle. La durée est
-- alors portée par period_days, qu'une contrainte rend obligatoire dans ce cas
-- et interdit dans les autres : deux sources de vérité pour la même durée
-- finiraient par diverger.
-- ---------------------------------------------------------------------------

ALTER TABLE tontines ADD COLUMN period_days INTEGER;

ALTER TABLE tontines DROP CONSTRAINT ck_tontines_frequency;

ALTER TABLE tontines
    ADD CONSTRAINT ck_tontines_frequency
        CHECK (frequency IN ('WEEKLY', 'TEN_DAYS', 'BIWEEKLY', 'MONTHLY', 'CUSTOM'));

ALTER TABLE tontines
    ADD CONSTRAINT ck_tontines_custom_period_days
        CHECK (
            (frequency = 'CUSTOM' AND period_days BETWEEN 1 AND 365)
            OR (frequency <> 'CUSTOM' AND period_days IS NULL)
        );
