-- Pod reset/destroy clears hints for all members and installations by resource.
CREATE INDEX activity_summary_game_kind ON activity_summary(game_id, kind);
CREATE INDEX activity_push_game_kind ON activity_push(game_id, kind);
