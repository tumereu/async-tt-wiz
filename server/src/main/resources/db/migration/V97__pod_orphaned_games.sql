-- DRF-039, DRF-235, DRF-236 (owner, 2026-11-22): a pod's games that carry on when its draft is cancelled are orphaned from
-- the pod: they keep their pod deck and name, but no longer count in the pod's Standings and Matches. Touches no row.
ALTER TABLE game ADD COLUMN pod_orphaned boolean NOT NULL DEFAULT false;
