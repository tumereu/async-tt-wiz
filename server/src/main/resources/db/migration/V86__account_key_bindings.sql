-- WEB-033 (owner, decision 23): the desktop layout's hotkeys that differ from the defaults, as JSON (hotkey id -> key, "" for
-- none); null: every hotkey on its default. Every browser of the account shows the same.
ALTER TABLE account ADD COLUMN key_bindings text;
