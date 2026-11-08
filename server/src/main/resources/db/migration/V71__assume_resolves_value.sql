-- Single version (owner 2026-11-08): "Assume resolves" is stored as one value ("assume") instead of its three flags
-- (alwaysAssumeResolves, assumeWhenNoMana, assumeNever), in each game's seat settings and each account's play defaults.
-- Every stored combination becomes the value the app read from it: Always first, then When they have no mana, then
-- Never, else Normal (also where a flag is missing). Nothing else changes; games and their events are untouched.
UPDATE engine_seat SET settings = ((settings::jsonb - 'alwaysAssumeResolves' - 'assumeWhenNoMana' - 'assumeNever')
    || jsonb_build_object('assume', jsonb_build_object('type', CASE
        WHEN (settings::jsonb ->> 'alwaysAssumeResolves')::boolean THEN 'always'
        WHEN (settings::jsonb ->> 'assumeWhenNoMana')::boolean THEN 'no-mana'
        WHEN (settings::jsonb ->> 'assumeNever')::boolean THEN 'never'
        ELSE 'normal' END)))::text
    WHERE NOT settings::jsonb ? 'assume';
UPDATE account SET play_defaults = ((play_defaults::jsonb - 'alwaysAssumeResolves' - 'assumeWhenNoMana' - 'assumeNever')
    || jsonb_build_object('assume', jsonb_build_object('type', CASE
        WHEN (play_defaults::jsonb ->> 'alwaysAssumeResolves')::boolean THEN 'always'
        WHEN (play_defaults::jsonb ->> 'assumeWhenNoMana')::boolean THEN 'no-mana'
        WHEN (play_defaults::jsonb ->> 'assumeNever')::boolean THEN 'never'
        ELSE 'normal' END)))::text
    WHERE play_defaults IS NOT NULL AND NOT play_defaults::jsonb ? 'assume';
