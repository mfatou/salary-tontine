-- ---------------------------------------------------------------------------
-- SalaryTontine - V4
-- Un employe peut participer a plusieurs tontines simultanement.
--
-- L'unicite (utilisateur, mois) interdisait structurellement d'enregistrer
-- l'effet de deux tontines sur un meme mois. Elle devient (utilisateur,
-- tontine, mois) : chaque tontine produit sa propre ligne de detail.
--
-- Le champ final_salary de chaque ligne porte desormais le salaire du mois
-- TOUTES tontines confondues, et non l'effet de la seule tontine de la ligne.
-- Il est recalcule sur toutes les lignes du mois a chaque generation, de sorte
-- que l'employe lise le meme montant quelle que soit la ligne consultee.
-- ---------------------------------------------------------------------------

ALTER TABLE salary_records
    DROP CONSTRAINT uk_salary_record_user_month;

ALTER TABLE salary_records
    ADD CONSTRAINT uk_salary_record_user_tontine_month
        UNIQUE (user_id, tontine_id, salary_month);

CREATE INDEX idx_salary_records_user_month_lookup
    ON salary_records (user_id, salary_month);
