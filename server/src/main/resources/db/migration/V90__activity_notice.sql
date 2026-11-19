-- LOB-078 (owner, 2026-11-19): a notification clears on every device of its player once nothing waits for them. The
-- notifications that may be showing on an account's devices (a feed page or the waiting check told them, or a push was
-- delivered), by the apps' notification key ("game:<id>", "invite:<id>", "pod:<id>", "pod-invite:<id>"), until the server
-- finds nothing waits for them and tells the devices.
CREATE TABLE activity_notice (
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    notice_key text NOT NULL,
    shown_at timestamptz NOT NULL,
    PRIMARY KEY (account_id, notice_key)
);
-- The browsers (WEB-052) that were sent a notice of a key: only they are sent its clear.
CREATE TABLE activity_notice_browser (
    generation uuid NOT NULL REFERENCES activity_installation(generation) ON DELETE CASCADE ON UPDATE CASCADE,
    notice_key text NOT NULL,
    PRIMARY KEY (generation, notice_key)
);
