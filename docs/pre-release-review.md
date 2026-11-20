# Pre-release review

Things to revisit before release. Entries are added only with the owner's approval.

- Undo and rewinds: going back never takes back the other player's real input, or anything new you were shown, without their approval (HIS-087), and what of theirs stays (staging, early answers, suggestions) still fits the board (SYS-058). Check the tests still cover this.
- Hidden/revealed information: what is shown to whom, and for how long (today: only while in the reveal/look panel).
- Event descriptions: timeline/event texts never reveal information hidden from the viewer at that event (hand, library, face-down, other players' looks); tests cover each case.
- Memory: measure client and server memory for a long, realistic game tree (10k+ events), in case recorded event details (e.g. concerned-field sets) cost too much.
- Known library places: a card whose place someone knows (a kept-revealed top card, a card put on top or bottom by a known action, one looked at where it lies) keeps it when a new line of play reshuffles the unknown cards (a branch, a rewind, a free undo); a real shuffle (search, mulligan) moves it. Check the tests still cover this.
- Alternate ways to cast: every alternate or additional way (bestow, evoke, dash, overload, foretell, plot, disguise, prototype, mutate, kicker, …) shows as a chip above the hand card (greyed when not possible now), never only in the cast panel or not at all (owner, 2026-11-05).
