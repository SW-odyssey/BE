-- Final v2 has one fixed audience in its contract, not a per-dataset attribute.
-- Keep the V9 checksum stable for isolated databases already used in validation.
alter table recommendation_dataset drop column population;
