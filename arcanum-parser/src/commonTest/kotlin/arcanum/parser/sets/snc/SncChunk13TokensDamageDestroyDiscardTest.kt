package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Streets of New Capenna chunk 13: tokens, damage, destroy and discard. */
class SncChunk13TokensDamageDestroyDiscardTest {
    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)
    private val devil = creatureToken("Devil", listOf(Color.Red), 1, 1, abilities = listOf(
        triggered(diesSelf, damage(1, toAny), targets = listOf(anyTarget))))

    // CR 111.10a, 603.6a, 605.1a, 106.1b
    @Test
    fun satchelOfPlenty() = card(
        name = "Satchel of Plenty",
        cost = "{1}{G}",
        type = "Artifact — Treasure",
        text = """
            When this artifact enters, create a 1/1 green and white Citizen creature token.
            {T}, Sacrifice this artifact: Add one mana of any color.
            {W}{U}{B}{R}{G}, {T}, Sacrifice this artifact: Draw three cards.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(citizen)),
        activated(cost(tap, sacrificeSelf), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{W}{U}{B}{R}{G}"), tap, sacrificeSelf), draw(3)),
    )

    // CR 602.5d, 113.6 (from the graveyard), 702.12 (can't be blocked)
    @Test
    fun disposableFootman() = card(
        name = "Disposable Footman",
        cost = "{U}",
        type = "Creature — Human Citizen",
        pt = "1/1",
        text = """
            {1}{U}, Exile this card from your graveyard: Create a 1/1 blue Fish creature token with "This token can't be blocked." Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{U}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            create(creatureToken("Fish", listOf(Color.Blue), 1, 1, abilities = listOf(static(Mechanic.CantBeBlocked)))),
            rules = asSorcery),
    )

    // CR 508.1, 111.4, 603.4 (intervening if), 700.4 (dies)
    @Test
    fun mephistoDiabolist() = card(
        name = "Mephisto Diabolist",
        cost = "{U}{B}{R}",
        type = "Creature — Vampire Warrior",
        pt = "1/4",
        text = """
            Deathtouch, haste
            Whenever this creature attacks, if you don't control a Devil token, create a tapped and attacking 1/1 red Devil creature token with "When this token dies, it deals 1 damage to any target."
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Haste,
        triggered(attacksSelf, doing(Effect.CreateToken(devil, n(1), Players.You, tapped = true, attacking = AttackTarget.Chosen)),
            interveningIf = Condition.Not(Condition.Exists(and(sub("Devil"), token, you), atLeast = 1))),
    )

    // CR 111.1, 118.12a (counter unless its controller pays), 603.6a
    @Test
    fun apprenticeAttendant() = card(
        name = "Apprentice Attendant",
        cost = "{2}{W}",
        type = "Creature — Cat Rogue",
        pt = "3/2",
        text = """
            When this creature enters, create a 1/1 blue Wizard creature token with "{1}, Sacrifice this token: Counter target noncreature spell unless its controller pays {1}."
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Wizard", listOf(Color.Blue), 1, 1, abilities = listOf(
            activated(cost(mana("{1}"), sacrificeSelf),
                Step.Pay(manaCost("{1}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
                targets = listOf(slot("spell", and(noncreatureSpell, spellFilter)))))))),
    )

    // CR 111.1, 603.6a, 400.3; the exile ability as in Fra chunk 6
    @Test
    fun fancyRevelers() = card(
        name = "Fancy Revelers",
        cost = "{2}{R}{G}{W}",
        type = "Creature — Elf Druid Rogue",
        pt = "5/3",
        text = """
            When this creature enters, create a 1/1 green and white Citizen creature token.
            {2}, Exile this card from your hand: Target land gains "{T}: Add {R}, {G}, or {W}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(citizen)),
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(activated(cost(tap),
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
                    colors = listOf('R', 'G', 'W').map { ColorRef.Literal(Color.of(it)!!) })), Players.You)))))),
                Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )

    // CR 700.2 (modes), 120.3, 111.1
    @Test
    fun alleyCharm() = card(
        name = "Alley Charm",
        cost = "{R}{G}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Alley Charm deals damage equal to the number of creatures you control to target creature or planeswalker.
            • Creatures you control get +1/+1 and gain trample until end of turn.
            • Create two 1/1 green and white Citizen creature tokens.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
                mode(damage(count(creatureYouControl), toObjects(target("permanent"))),
                    targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
                mode(Step.Do(Effect.ApplyMechanics(listOf(pt(1, 1), has(Keyword.Trample)),
                    Affected.Objects(Objects.Each(creatureYouControl)), Duration.EndOfTurn))),
                mode(create(citizen, count = n(2))),
            ))),
    )

    // CR 603.2c (one or more), 701.21 (sacrifice), 603.2i (once each turn), 120.3
    @Test
    fun anvilBoss() = card(
        name = "Anvil Boss",
        cost = "{2}{B}{R}",
        type = "Creature — Human Warrior",
        pt = "3/4",
        text = """
            Whenever you sacrifice one or more other creatures, this creature deals 2 damage to each opponent. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Sacrifice(and(another, creature, you)), oneOrMore = true),
            damage(2, eachOpponent), perTurn = 1),
    )

    // CR 120.3, 111.1 (tokens are not nontoken)
    @Test
    fun blazingAria() = card(
        name = "Blazing Aria",
        cost = "{R}{G}{W}",
        type = "Sorcery",
        text = """
            Blazing Aria deals 3 damage to each nontoken creature.
        """,
    ) parsesTo abilities(
        spell(damage(3, toObjects(Objects.Each(and(not(token), creature))))),
    )

    // CR 120.3, 701.21 (sacrifice), 602.1
    @Test
    fun kindlingArsonist() = card(
        name = "Kindling Arsonist",
        cost = "{2}{R}",
        type = "Creature — Lizard Shaman",
        pt = "2/2",
        text = """
            {1}, {T}: This creature deals X damage to any target, where X is the number of permanents you've sacrificed this turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap),
            damage(Value.EventsThisTurn(EventPattern.Sacrifice(you), Measure.Objects), toAny),
            targets = listOf(anyTarget)),
    )

    // CR 700.2 (modes), 120.3, 701.8a
    @Test
    fun primedToBrawl() = card(
        name = "Primed to Brawl",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Primed to Brawl deals 5 damage to target creature or planeswalker.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
                mode(damage(5, toObjects(target("permanent"))),
                    targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
                mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            ))),
    )

    // CR 700.2, 603.6a, 701.8a, 406.2, 119.1
    @Test
    fun tidyCrew() = card(
        name = "Tidy Crew",
        cost = "{4}{G}{G}",
        type = "Creature — Human Citizen",
        pt = "6/6",
        text = """
            When this creature enters, choose one —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Exile target card from a graveyard.
            • You gain 4 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
            mode(gainLife(4)),
        ))),
    )

    // CR 506.4, 701.8a
    @Test
    fun woundShot() = card(
        name = "Wound Shot",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Destroy target attacking creature.
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 701.8a, 120.3, 400.7 (the artifacts counted are those that reached a graveyard this turn)
    @Test
    fun structuralMishap() = card(
        name = "Structural Mishap",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Destroy all artifacts, then Structural Mishap deals damage to each creature equal to the number of artifacts that were put into graveyards from the battlefield this turn.
        """,
    ) parsesTo abilities(
        spell(
            destroy(Objects.Each(artifact)),
            damage(Value.EventsThisTurn(EventPattern.ZoneChange(artifact, from = Zone.Battlefield, to = Zone.Graveyard), Measure.Objects),
                toObjects(Objects.Each(creature))),
        ),
    )

    // CR 700.2d (choose two), 122.1c (shield counter), 603.6a, 111.1
    @Test
    fun machineOfTrade() = card(
        name = "Machine of Trade",
        cost = "{4}{G}{G}{G}",
        type = "Creature — Elemental",
        pt = "7/7",
        text = """
            Reach, trample
            When this creature enters, choose two —
            • Destroy target artifact or enchantment.
            • Target player gains 5 life.
            • Create a 4/4 green Rhino Warrior creature token.
            • Put a shield counter on a creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(gainLife(5, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(create(creatureToken("Rhino Warrior", listOf(Color.Green), 4, 4))),
            mode(Step.Choose(Choice.Objects(and(creature, you), n(1), n(1)), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen"), 1, CounterKind.Shield)),
            min = 2, max = 2,
        ))),
    )

    // CR 701.9a (discard), 603.6a, 115.1
    @Test
    fun cruelCourtClerk() = card(
        name = "Cruel Court Clerk",
        cost = "{1}{B}",
        type = "Creature — Human Advisor",
        pt = "1/1",
        text = """
            When this creature enters, target opponent discards a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, Players.Target("opponent")), targets = listOf(targetOpponent)),
    )
}
