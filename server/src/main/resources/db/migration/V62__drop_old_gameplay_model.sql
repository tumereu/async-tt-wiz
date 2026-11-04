-- Engine-responsiveness R4b: every game plays on engine state (I-D56), and V59 emptied the old model's history tables;
-- nothing reads or writes them any more. They are dropped, deleting no game: game_play_mutation (V14's retained
-- copy), the endpoint index (V22), the tree controls (V16), and the old history's events and groups (V11). Each is
-- referenced only by the ones dropped before it; without CASCADE a table anything else still referenced would fail here.
DROP TABLE game_play_mutation;
DROP TABLE gameplay_endpoint_edge;
DROP TABLE gameplay_endpoint_record;
DROP TABLE gameplay_tree_control;
DROP TABLE gameplay_mutation;
DROP TABLE gameplay_group;
