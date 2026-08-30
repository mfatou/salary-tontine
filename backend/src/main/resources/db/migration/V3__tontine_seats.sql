-- ---------------------------------------------------------------------------
-- SalaryTontine - V3
-- Nombre de places d'une tontine.
--
-- La duree d'un cycle vaut exactement un mois par participant : la date de fin
-- ne peut donc pas etre choisie independamment. En declarant un nombre de
-- places des la creation, le comptable fixe indirectement la fin du cycle, ce
-- qui permet de l'annoncer aux employes avant meme l'activation.
--
-- La colonne reste facultative : sans elle, la tontine accepte des inscriptions
-- sans limite et sa fin n'est connue qu'a l'activation.
-- ---------------------------------------------------------------------------

ALTER TABLE tontines
    ADD COLUMN target_member_count INTEGER;

ALTER TABLE tontines
    ADD CONSTRAINT ck_tontines_target_member_count
        CHECK (target_member_count IS NULL OR target_member_count >= 2);
