UPDATE game SET starter_choice = '{"accountId":null}' WHERE starter_choice IS NULL;
ALTER TABLE game ALTER COLUMN starter_choice SET DEFAULT '{"accountId":null}';
ALTER TABLE game ALTER COLUMN starter_choice SET NOT NULL;
