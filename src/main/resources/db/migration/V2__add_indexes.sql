-- Bug 6 fix: composite PK on dependencies(from_entity_id, to_entity_id)
-- only indexes lookups starting from "from_entity_id" (Postgres uses the
-- leftmost column of a composite index automatically). Queries asking
-- "who depends ON this entity?" (i.e. WHERE to_entity_id = ?) would do
-- a full table scan without this.
CREATE INDEX idx_deps_to ON dependencies(to_entity_id);

CREATE INDEX idx_code_entities_repo ON code_entities(repo_id);


CREATE INDEX idx_repositories_owner ON repositories(owner_id);