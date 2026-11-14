package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 11: scavenge, scry, spectacle, transmute, unleash, graft and plain apply-effect cards. */
class RvrChunk11ScavengeScrySpectacleTransmuteUnleashTest {
    private val yourCreatures = Objects.Each(and(creature, you))
    private val scavengeText = "({cost}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)"
    private val transmuteText = "({cost}, Discard this card: Search your library for a card with the same mana value as this card, reveal it, put it into your hand, then shuffle. Transmute only as a sorcery.)"
    private val spectacleText = "(You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn.)"
    private val unleashText = "(You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)"
    private fun scavengeLine(cost: String) = "Scavenge $cost " + scavengeText.replace("{cost}", cost)
    private fun transmuteLine(cost: String) = "Transmute $cost " + transmuteText.replace("{cost}", cost)
    private fun scavenge(cost: String) = Keyword.Scavenge(manaCost(cost))
    private fun transmute(cost: String) = Keyword.Transmute(manaCost(cost))

    // CR 702.97a (scavenge)
    @Test
    fun gutterWeevilLookalike() = card(
        name = "Gutter Weevil", cost = "{1}{G}", type = "Creature — Insect", pt = "2/2",
        text = scavengeLine("{5}{G}"),
    ) parsesTo abilities(scavenge("{5}{G}"))

    // CR 702.97a, 702.14c (swampwalk is its own keyword)
    @Test
    fun mireShamblerLookalike() = card(
        name = "Mire Shambler", cost = "{2}{B}", type = "Creature — Zombie", pt = "2/1",
        text = """
            Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
            ${scavengeLine("{2}{B}")}
        """,
    ) parsesTo abilities(Keyword.Landwalk(sub("Swamp"), "Swampwalk"), scavenge("{2}{B}"))

    // CR 702.97a, 202.2 (a hybrid card is both colours), zero scavenge cost
    @Test
    fun wispLarvaLookalike() = card(
        name = "Wisp Larva", cost = "{B/G}", type = "Creature — Plant Zombie", pt = "1/1",
        text = scavengeLine("{0}"),
    ) parsesTo abilities(scavenge("{0}"))

    // CR 701.22a (scry), 603.2 (cast trigger), 611.2a
    @Test
    fun burningSeerLookalike() = card(
        name = "Burning Seer", cost = "{1}{R}", type = "Creature — Human Wizard", pt = "1/3",
        text = "Whenever you cast a noncreature spell, this creature gets +1/+0 until end of turn, then scry 1.",
    ) parsesTo abilities(triggered(youCast(noncreatureSpell), pump(Objects.Self, 1, 0), scry(1)))

    // CR 701.22a (scry in an upkeep trigger), 701.12a (exchange control), 307.5 (sorcery-speed activation)
    @Test
    fun gazeEverywhereLookalike() = card(
        name = "Gaze Everywhere", cost = "{2}{U}", type = "Enchantment",
        text = """
            At the beginning of your upkeep, scry 1.
            {5}{U}: Exchange control of this enchantment and target nonland permanent. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep(), scry(1)),
        activated(cost(mana("{5}{U}")), doing(Effect.ExchangeControl(Objects.Self, target("permanent"))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))), rules = asSorcery),
    )

    // CR 702.137a (spectacle), 120.3 (damage to yourself), 121.1
    @Test
    fun bladeTumblerLookalike() = card(
        name = "Blade Tumbler", cost = "{4}{B}", type = "Creature — Human Rogue", pt = "3/2",
        text = """
            Spectacle {2}{B} $spectacleText
            When this creature enters, it deals 1 damage to you and you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Spectacle(manaCost("{2}{B}")),
        triggered(entersSelf, damage(1, Recipients(players = Players.You)), draw(1)),
    )

    // CR 702.137a, 611.2a (exiled cards playable until the end of your next turn)
    @Test
    fun lightUpTheDaisLookalike() = card(
        name = "Light Up the Dais", cost = "{2}{R}", type = "Sorcery",
        text = """
            Spectacle {R} $spectacleText
            Exile the top two cards of your library. Until the end of your next turn, you may play those cards.
        """,
    ) parsesTo abilities(
        Keyword.Spectacle(manaCost("{R}")),
        spell(
            doing(Effect.Move(Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), Destination(zone = Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
        ),
    )

    // CR 702.137a, 120.3 (damage to any target)
    @Test
    fun skewerTheHecklersLookalike() = card(
        name = "Skewer the Hecklers", cost = "{2}{R}", type = "Sorcery",
        text = """
            Spectacle {R} $spectacleText
            Skewer the Hecklers deals 3 damage to any target.
        """,
    ) parsesTo abilities(Keyword.Spectacle(manaCost("{R}")), spell(damage(3, toAny), targets = listOf(anyTarget)))

    // CR 702.53a (transmute), 702.3b (defender), 702.9a (flying)
    @Test
    fun driftOfMirages() = card(
        name = "Drift of Mirages", cost = "{2}{U}", type = "Creature — Spirit", pt = "0/5",
        text = """
            Defender (This creature can't attack.)
            Flying
            ${transmuteLine("{1}{U}{U}")}
        """,
    ) parsesTo abilities(Keyword.Defender, Keyword.Flying, transmute("{1}{U}{U}"))

    // CR 702.53a, 701.6a (counter target instant or sorcery spell)
    @Test
    fun muddleTheDraught() = card(
        name = "Muddle the Draught", cost = "{U}{U}", type = "Instant",
        text = """
            Counter target instant or sorcery spell.
            ${transmuteLine("{1}{U}{U}")}
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter)))),
        transmute("{1}{U}{U}"),
    )

    // CR 702.98a (unleash), 702.98b (can't block while it has a +1/+1 counter)
    @Test
    fun bloodmawGiantLookalike() = card(
        name = "Bloodmaw Giant", cost = "{2}{R}{R}", type = "Creature — Giant", pt = "4/3",
        text = """
            Trample
            Unleash $unleashText
        """,
    ) parsesTo abilities(Keyword.Trample, Keyword.Unleash)

    // CR 702.98a, 702.2a (deathtouch)
    @Test
    fun thrillKillBruteLookalike() = card(
        name = "Thrill-Kill Brute", cost = "{1}{B}", type = "Creature — Human Assassin", pt = "1/2",
        text = """
            Deathtouch
            Unleash $unleashText
        """,
    ) parsesTo abilities(Keyword.Deathtouch, Keyword.Unleash)

    // CR 709.3: each half of a split card is read on its own; 202.2 (hybrid cost)
    @Test
    fun bedaubBedimLookalike() = cardFaces(
        Layout.Split,
        card(name = "Bedaub", cost = "{B/R}{B/R}", type = "Instant", text = "Target creature gets +3/-3 until end of turn.") to abilities(
            spell(pump(target("creature"), 3, -3), targets = listOf(slot("creature", creature))),
        ),
        card(name = "Bedim", cost = "{4}{B}{R}", type = "Instant",
            text = "Destroy target nonbasic land. Bedim deals 2 damage to target opponent or planeswalker.") to abilities(
            spell(
                destroy(target("land")),
                damage(2, Recipients(target = "opponent or planeswalker")),
                targets = listOf(
                    slot("land", and(not(ObjectFilter.Super(Supertype.Basic)), land)),
                    TargetSlot("opponent or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Opponent), min = n(1), max = n(1)),
                ),
            ),
        ),
    )

    // CR 702.2b (deathtouch granted until end of turn), 121.1
    @Test
    fun bladeglazeLookalike() = card(
        name = "Bladeglaze", cost = "{1}{B}", type = "Instant",
        text = """
            Target creature gains deathtouch until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(spell(gains(target("creature"), Keyword.Deathtouch), draw(1), targets = listOf(slot("creature", creature))))

    // CR 702.10a (haste), 702.7a (first strike), 611.2a
    @Test
    fun borosHedgemageLookalike() = card(
        name = "Boros Hedgemage", cost = "{R/W}{R/W}", type = "Creature — Human Wizard", pt = "2/2",
        text = """
            {1}{R}: Target creature gains haste until end of turn.
            {1}{W}: Target creature gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{R}")), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{1}{W}")), gains(target("creature"), Keyword.FirstStrike), targets = listOf(slot("creature", creature))),
    )

    // CR 611.2c (affects only creatures you control as it resolves)
    @Test
    fun burnBrighter() = card(
        name = "Burn Brighter", cost = "{2}{R}", type = "Instant",
        text = "Creatures you control get +2/+0 until end of turn.",
    ) parsesTo abilities(spell(untilEndOfTurn(yourCreatures, pt(2, 0))))

    // CR 611.2a (a negative toughness change)
    @Test
    fun lesserForgelingLookalike() = card(
        name = "Lesser Forgeling", cost = "{3}{R}{R}", type = "Creature — Elemental", pt = "3/4",
        text = "{1}{R}: This creature gets +3/-3 until end of turn.",
    ) parsesTo abilities(activated(cost(mana("{1}{R}")), pump(Objects.Self, 3, -3)))

    // CR 702.58a (graft), 122.1b (+1/+1 counters), 702.9a
    @Test
    fun neonSquirterLookalike() = card(
        name = "Neon Squirter", cost = "{4}{U}", type = "Creature — Beast Mutant", pt = "0/0",
        text = """
            Graft 3 (This creature enters with three +1/+1 counters on it. Whenever another creature enters, you may move a +1/+1 counter from this creature onto it.)
            {1}: Target creature with a +1/+1 counter on it gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Graft(3),
        activated(cost(mana("{1}")), gains(target("creature"), Keyword.Flying),
            targets = listOf(slot("creature", and(creature, ObjectFilter.HasCounter(p1p1, 1))))),
    )

    // CR 702.9a, 702.19a, 509.1a, 104.2b (win the game), 603.4 (intervening if), 613.1 (control change)
    @Test
    fun hellkiteMagnateLookalike() = card(
        name = "Hellkite Magnate", cost = "{4}{R}{R}", type = "Creature — Dragon", pt = "6/5",
        text = """
            Flying, trample
            Whenever this creature deals combat damage to a player, gain control of all artifacts that player controls.
            At the beginning of your upkeep, if you control twenty or more artifacts, you win the game.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(combatDamageToAPlayer, doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)),
            Affected.Objects(Objects.Each(and(artifact, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))))), Duration.Indefinitely))),
        triggered(yourUpkeep(), doing(Effect.WinGame(Players.You)), interveningIf = youControl(artifact, 20)),
    )

    // CR 509.1b (can't be blocked except by), 702.3a (defender), 702.10a
    @Test
    fun tinAlleyDodgerLookalike() = card(
        name = "Tin Alley Dodger", cost = "{R}", type = "Creature — Goblin Rogue", pt = "1/1",
        text = """
            Haste
            {R}: This creature can't be blocked this turn except by creatures with defender.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(mana("{R}")), untilEndOfTurn(Objects.Self, Mechanic.CanBeBlockedOnlyBy(kw(Keyword.Defender)))),
    )

    // CR 508.1m (attack trigger), 611.2a
    @Test
    fun vedalkenMuddlerLookalike() = card(
        name = "Vedalken Muddler", cost = "{1}{U}", type = "Creature — Vedalken Wizard", pt = "2/1",
        text = "Whenever this creature attacks, target creature an opponent controls gets -2/-0 until end of turn.",
    ) parsesTo abilities(triggered(attacksSelf, pump(target("creature"), -2, 0), targets = listOf(slot("creature", creatureOpponentControls))))
}
