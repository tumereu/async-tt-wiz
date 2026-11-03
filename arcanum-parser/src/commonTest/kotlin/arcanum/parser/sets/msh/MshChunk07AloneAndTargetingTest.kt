package arcanum.parser.sets.msh

import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 07: creatures attacking alone (CR 506.5: the only creature declared as an attacker; a trigger, CR
 * 508.1a, and a target "that's attacking alone") and spells that target your creatures (CR 115.1: the cast trigger's spell and what
 * it targets). A granted activated ability with "any other target" (CR 115.4) and a granted base power (layer 7b); a copy of the
 * spell (CR 707.10) and new targets for it (CR 115.7).
 */
class MshChunk07AloneAndTargetingTest {
    private val aloneYours = attacksAlone(creatureYouControl)

    @Test
    fun operativeNineFieldLiaison() = card(
        name = "Operative Nine, Field Liaison",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Spy Hero",
        text = "Whenever a creature you control attacks alone, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(aloneYours, Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate(n(1))))),
    )

    @Test
    fun agentsOfTheBureau() = card(
        name = "Agents of the Bureau",
        cost = "{2}{W}",
        type = "Creature — Human Spy Hero",
        text = "Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(aloneYours, pump(Objects.EventObject(EventRole.Object), 1, 1)),
    )

    // CR 506.5: a target that's attacking alone
    @Test
    fun crowdOfLoyalFans() = card(
        name = "Crowd of Loyal Fans",
        cost = "{W}",
        type = "Creature — Human Citizen",
        text = "{T}: Target creature you control that's attacking alone gets +1/+0 until end of turn. You gain 1 life.",
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(tap), pump(target("alone"), 1, 0), gainLife(1),
            targets = listOf(slot("alone", and(creatureYouControl, ObjectFilter.AttackingAlone)))),
    )

    @Test
    fun bureauSpyKit() = card(
        name = "Bureau Spy Kit",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1.
            Whenever equipped creature attacks alone, untap it and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)),
        triggered(attacksAlone(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))), untapIt(Objects.EventObject(EventRole.Object)), scry(1)),
        Keyword.Equip(manaCost("{1}")),
    )

    @Test
    fun serpentCultInfiltration() = card(
        name = "Serpent Cult Infiltration",
        cost = "{3}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, target opponent discards two cards.
            Whenever a creature you control attacks alone, target opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
        triggered(aloneYours, loseLife(1, Players.Target("opponent")), gainLife(1), targets = listOf(targetOpponent)),
    )

    @Test
    fun crimsonSpiderDoubleAgent() = card(
        name = "Crimson Spider, Double Agent",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Hero Villain",
        text = """
            Deathtouch
            Whenever a creature you control attacks alone, it gains first strike and menace until end of turn. (It can't be blocked except by two or more creatures.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(aloneYours, gains(Objects.EventObject(EventRole.Object), Keyword.FirstStrike, Keyword.Menace)),
    )

    // a flavor word (CR 207.2d)
    @Test
    fun lucasStoneIronskin() = card(
        name = "Lucas Stone, Ironskin",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Hero",
        text = "Unbreakable Skin — Whenever Lucas Stone attacks alone, he gets +2/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy him.)",
        pt = "2/5",
    ) parsesTo abilities(
        triggered(attacksAlone(ObjectFilter.Is(Objects.Self)), pump(Objects.Self, 2, 0, has(Keyword.Indestructible)), abilityWord = "Unbreakable Skin"),
    )

    // CR 115.1: a spell that targets a creature you control, as it is cast
    @Test
    fun kiriWynnStreetSamurai() = card(
        name = "Kiri Wynn, Street Samurai",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Samurai Hero",
        text = "Whenever you cast a spell that targets a creature you control, put a +1/+1 counter on Kiri Wynn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(youCastTargeting(creatureYouControl), counters(Objects.Self), scry(1)),
    )

    @Test
    fun larkspurAceAgent() = card(
        name = "Larkspur, Ace Agent",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Spy Hero",
        text = """
            Double strike
            Whenever you cast a spell that targets a creature you control, put a +1/+1 counter on Larkspur.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(youCastTargeting(creatureYouControl), counters(Objects.Self)),
    )

    // layer 7b: a granted base power (not characteristic-defining once granted)
    @Test
    fun missWondrousKiranShah() = card(
        name = "Miss Wondrous, Kiran Shah",
        cost = "{2}{U}",
        type = "Legendary Creature — Mutant Inhuman Hero",
        text = """
            Reach, vigilance
            You have no maximum hand size.
            Embiggen Fist — Whenever you cast a spell that targets a creature you control, draw a card. Until end of turn, Miss Wondrous gains "Miss Wondrous's base power is equal to the number of cards in your hand."
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        playerStatic(Mechanic.NoMaximumHandSize),
        triggered(youCastTargeting(creatureYouControl), draw(1), gains(Objects.Self,
            static(Mechanic.SetPowerToughness(count(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), null))),
            abilityWord = "Embiggen Fist"),
    )

    // CR 115.4: "any other target" is any target but this creature
    @Test
    fun jadeFistLivingWeapon() = card(
        name = "Jade Fist, Living Weapon",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Warrior Hero",
        text = "Whenever you cast a spell that targets a creature you control, Jade Fist gains \"{T}: Jade Fist deals damage equal to his power to any other target\" until end of turn.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(youCastTargeting(creatureYouControl), gains(Objects.Self, activated(cost(tap), damage(power(Objects.Self), toAny),
            targets = listOf(TargetSlot("any", TargetKind.Either(and(or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle)), another), PlayerFilter.Any), min = n(1), max = n(1)))))),
    )

    // CR 115.7: new targets for the spell that targeted it
    @Test
    fun bouncebackNewWarrior() = card(
        name = "Bounceback, New Warrior",
        cost = "{2}{U/R}",
        type = "Legendary Creature — Human Hero",
        text = "Whenever a player casts a spell that targets Bounceback, he gets +2/+2 until end of turn. You may choose new targets for that spell.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Targets(objects = Objects.Self), PlayerFilter.Any)), pump(Objects.Self, 2, 2),
            Step.Do(Effect.ChangeTargets(Objects.EventObject(EventRole.Object), Players.You, optional = true))),
    )

    // the creatures the cast spell targets (CR 115.1); creatures with flying can't attack you or block your creatures
    @Test
    fun tempestWindrider() = card(
        name = "Tempest, Windrider",
        cost = "{1}{G}{W}{W}",
        type = "Legendary Creature — Mutant Hero",
        text = """
            Flying
            Creatures with flying can't attack you or block creatures you control.
            Whenever you cast a spell that targets one or more creatures, those creatures gain flying until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantAttack(target = Recipients(players = Players.You)), Mechanic.CantBlock(creatureYouControl),
            affects = Objects.Each(and(creature, kw(Keyword.Flying.kind)))),
        triggered(youCastTargeting(creature), gains(Objects.Each(and(creature, ObjectFilter.TargetedBy(Objects.EventObject(EventRole.Object)))), Keyword.Flying)),
    )

    // CR 707.10: a copy of the spell, with new targets
    @Test
    fun grakThulVorr() = card(
        name = "Grak Thul Vorr",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Alien Dragon Villain",
        text = """
            Flying
            Whenever you cast an instant or sorcery spell that targets an artifact or land, copy that spell. You may choose new targets for the copy. Put two +1/+1 counters on Grak Thul Vorr.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCastTargeting(or(artifact, land), and(or(instant, sorcery), spellFilter)),
            Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)), counters(Objects.Self, 2)),
    )

}
