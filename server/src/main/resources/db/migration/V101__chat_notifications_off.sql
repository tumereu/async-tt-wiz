-- LOB-276 (owner, 2026-11-27): the Chat kind of notification is off unless the player switches it on. An account's stored
-- switches (written whole when it switched any kind) hold Chat on only because it was the default then: off now. Switches
-- left equal to the new default are stored as none. Accounts without stored switches follow the new default by themselves.
UPDATE account SET notification_settings = jsonb_set(notification_settings::jsonb, '{chat}', 'false')::text
    WHERE notification_settings IS NOT NULL AND notification_settings::jsonb ->> 'chat' = 'true';
UPDATE account SET notification_settings = NULL
    WHERE notification_settings::jsonb = '{"yourMove":true,"reminders":true,"chat":false,"invitations":true,"draft":true,"gameOver":true}'::jsonb;
