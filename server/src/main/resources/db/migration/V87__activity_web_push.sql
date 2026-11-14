-- WEB-050, WEB-051: a browser's Web Push subscription (its push service's endpoint, the browser's keys and the kinds of
-- notification it wants, as JSON); null: none. A browser's installation has it in place of an Android push_token.
ALTER TABLE activity_installation ADD COLUMN web_push text;
