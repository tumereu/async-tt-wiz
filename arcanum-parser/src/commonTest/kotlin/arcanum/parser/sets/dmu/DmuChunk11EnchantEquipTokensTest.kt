package arcanum.parser.sets.dmu

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 11: Auras and Equipment that grant while the host is legendary (CR 303.4, 301.5, 702.5, 702.6), additional costs
 * (CR 118.8), cost reductions (CR 601.2f), "can't be blocked" and "can't gain life" (CR 509.1b, 119.7), counter modes and token creation.
 */
class DmuChunk11EnchantEquipTokensTest {
    private val birdToken = creatureToken("Bird", listOf(Color.White), 1, 1, listOf(Keyword.Flying))
    private val legendaryHost = Condition.ObjectMatches(equipped, legendary)

    // CR 303.4, 702.5, 702.21a: an Aura granting a triggered ability, and a pump plus ward only while the host is legendary
    @Test
    fun vigilStudy() = card(
        name = "Vigil Study", cost = "{U}", type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has "Whenever this creature deals combat damage to a player, draw a card."
            As long as enchanted creature is legendary, it gets +1/+1 and has ward {1}. (Whenever enchanted creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(has(triggered(combatDamageToAPlayer, draw(1))), affects = enchanted),
        static(pt(1, 1), has(Keyword.Ward(manaCost("{1}"))), affects = enchanted, condition = legendaryHost),
    )

    // CR 509.1b: "can't block" until end of turn; CR 303.4
    @Test
    fun hammerfistBoon() = card(
        name = "Hammerfist Boon", cost = "{R}", type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, target creature can't block this turn.
            Enchanted creature gets +1/+1 and has haste.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, untilEndOfTurn(target("creature"), Mechanic.CantBlock(attackers = ObjectFilter.Any)), targets = listOf(slot("creature", creature))),
        static(pt(1, 1), has(Keyword.Haste), affects = enchanted),
    )

    // CR 301.5, 702.6
    @Test
    fun keepsakeOfChampions() = card(
        name = "Keepsake of Champions", cost = "{2}", type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+1.
            As long as equipped creature is legendary, it has trample and haste.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 1), affects = equipped),
        static(has(Keyword.Trample, Keyword.Haste), affects = equipped, condition = legendaryHost),
        equip("{2}"),
    )

    // CR 301.5, 702.6a
    @Test
    fun conquerorsCleaver() = card(
        name = "Conqueror's Cleaver", cost = "{1}", type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = equipped),
        equip("{2}"),
    )

    // CR 118.8: sacrificing a creature as an additional cost
    @Test
    fun marrowSplinters() = card(
        name = "Marrow Splinters", cost = "{B}", type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature)))),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 118.8: discarding a card as an additional cost
    @Test
    fun gambleOfEmbers() = card(
        name = "Gamble of Embers", cost = "{1}{R}", type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2)),
    )

    // CR 702.2c
    @Test
    fun cinderGnatSwarm() = card(
        name = "Cinder Gnat Swarm", cost = "{B}", type = "Creature — Phyrexian Insect", pt = "1/1",
        text = """
            Flying
            {B}: This creature gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{B}")), gains(Objects.Self, Keyword.Deathtouch)),
    )

    // CR 701.21, 608.2c: "if you do" after an optional sacrifice
    @Test
    fun pyrewingDrake() = card(
        name = "Pyrewing Drake", cost = "{4}{R}{R}", type = "Creature — Dragon", pt = "5/3",
        text = """
            Flying
            Whenever this creature attacks, you may sacrifice another creature. If you do, this creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf,
            Step.Pay(cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(ObjectFilter.Is(Objects.Self)), creature), count = n(1)))),
                payer = Players.You, bind = "paid"),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(gains(Objects.Self, Keyword.DoubleStrike)))),
    )

    // CR 508.1, 702.7 (untap), 700.1: an attack trigger affecting the other attackers by quality
    @Test
    fun tarinDOrrel() = card(
        name = "Tarin D'Orrel, Banner Rider", cost = "{1}{R}{R}{W}", type = "Legendary Creature — Human Knight", pt = "3/3",
        text = """
            Vigilance, trample
            Whenever Tarin D'Orrel attacks, all other attacking creatures you control get +1/+1 until end of turn. Other red attacking creatures you control gain trample until end of turn. Untap each other white attacking creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance, Keyword.Trample,
        triggered(attacksSelf,
            pump(Objects.Each(and(another, attacking, creature, you)), 1, 1),
            gains(Objects.Each(and(another, ObjectFilter.OfColor(ColorRef.Literal(Color.Red)), attacking, creature, you)), Keyword.Trample),
            untapIt(Objects.Each(and(another, ObjectFilter.OfColor(ColorRef.Literal(Color.White)), attacking, creature, you)))),
    )

    // CR 509.1b: "can't be blocked" only while a condition holds
    @Test
    fun mirageFigment() = card(
        name = "Mirage Figment", cost = "{1}{U}", type = "Creature — Illusion", pt = "2/1",
        text = """
            Vigilance
            This creature can't be blocked as long as you've cast an instant or sorcery spell this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.CantBeBlocked, condition = Condition.HappenedThisTurn(
            EventPattern.Cast(or(instant, sorcery), PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
    )

    // CR 119.7: opponents can't gain life
    @Test
    fun knaveOfMidnightVeil() = card(
        name = "Knave of Midnight Veil", cost = "{1}{B}", type = "Creature — Human Knight", pt = "2/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Your opponents can't gain life.
            {1}{B}: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        playerStatic(Mechanic.CantGainLife, players = Players.Each(PlayerFilter.Opponent)),
        activated(cost(mana("{1}{B}")), pump(Objects.Self, 1, 1)),
    )

    // CR 601.2f: a cost reduction by the greatest power
    @Test
    fun magmaColossus() = card(
        name = "Magma Colossus", cost = "{7}{R}", type = "Creature — Hellion", pt = "5/5",
        text = """
            This spell costs {X} less to cast, where X is the greatest power among creatures you control.
            Trample
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
        Keyword.Trample,
    )

    // CR 601.2f: one less for each creature card in your graveyard
    @Test
    fun wrigglingCarrion() = card(
        name = "Wriggling Carrion", cost = "{6}{B}", type = "Creature — Zombie Giant", pt = "5/5",
        text = """
            This spell costs {1} less to cast for each creature card in your graveyard.
            Deathtouch
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(creature))))),
        Keyword.Deathtouch,
    )

    // CR 700.2, 701.6a, 701.8: up-to-one modes, countering an ability, destroy with its controller drawing
    @Test
    fun erwinRevived() = card(
        name = "Erwin Revived", cost = "{2}{U}{B}", type = "Legendary Creature — Phyrexian Human Wizard", pt = "3/2",
        text = """
            Flash
            When Erwin Revived enters, choose up to one —
            • Counter target spell, activated ability, or triggered ability. Its controller draws a card.
            • Destroy another target creature or planeswalker. Its controller draws a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(modes = modes(min = 0, max = 1, options = arrayOf(
            mode(doing(Effect.Counter(target("spell"))), draw(1, Players.ControllerOf(target("spell"))),
                targets = listOf(slot("spell", or(spellFilter,
                    and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
                    and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered)))))),
            mode(destroy(target("permanent")), draw(1, Players.ControllerOf(target("permanent"))),
                targets = listOf(slot("permanent", and(another, or(creature, planeswalker))))),
        )))),
    )

    // CR 700.2: a three-way mode choice with a token, a bounce and a draw
    @Test
    fun aetherWeaver() = card(
        name = "Aether Weaver", cost = "{2}{U}", type = "Creature — Human Wizard", pt = "2/1",
        text = """
            When this creature enters, choose one —
            • Create a 1/1 white Bird creature token with flying.
            • Return another target nonland permanent to its owner's hand.
            • Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(options = arrayOf(
            mode(create(birdToken)),
            mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonland, permanentFilter)))),
            mode(draw(1)),
        )))),
    )

    // CR 111.1
    @Test
    fun rallyingMuster() = card(
        name = "Rallying Muster", cost = "{3}{W}", type = "Sorcery",
        text = "Create three 1/1 white Soldier creature tokens.",
    ) parsesTo abilities(spell(create(soldier, n(3))))

    // CR 603.7a, 111.1: a token sacrificed at the next end step; a tap ability restricted by a condition
    @Test
    fun laganHandOfMalice() = card(
        name = "Lagan, Hand of Malice", cost = "{1}{B}{R}", type = "Legendary Creature — Human Shaman", pt = "1/3",
        text = """
            At the beginning of combat on your turn, create a 2/1 red Elemental creature token with trample and haste. Sacrifice it at the beginning of the next end step.
            {T}: Search your library for a card, put it into your hand, then shuffle. Activate only if five or more creatures died this turn.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            create(creatureToken("Elemental", listOf(Color.Red), 2, 1, listOf(Keyword.Trample, Keyword.Haste)), bind = "token"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token")))))))),
        activated(cost(tap), search(cardFilter, bind = "found"), move(Objects.Bound("found"), Zone.Hand), shuffle,
            rules = anyTime.copy(condition = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 5, measure = Measure.Objects))),
    )

    // CR 508.4, 111.1: a token tapped and attacking; CR 118.9, 601.3e: cast from the graveyard for an alternative cost
    @Test
    fun squibDoubtfulReeve() = card(
        name = "Squib, Doubtful Reeve", cost = "{2}{R}", type = "Legendary Creature — Goblin Noble", pt = "2/2",
        text = """
            Haste
            Whenever Squib attacks, create a 1/1 red Goblin creature token that's tapped and attacking.
            You may cast this card from your graveyard by paying {3}{R} and exiling four other cards from your graveyard rather than paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, Step.Do(Effect.CreateToken(creatureToken("Goblin", listOf(Color.Red), 1, 1), n(1), Players.You, tapped = true,
            attacking = AttackTarget.Chosen))),
        static(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast,
            cost = cost(mana("{3}{R}"), CostPart.Exile(CostObjects.Choose(and(another, cardFilter), count = n(4)), from = Zone.Graveyard)),
            holder = PlayerFilter.You)),
    )

    // CR 603.2: a cast trigger creating a token
    @Test
    fun tulaKennerud() = card(
        name = "Tula Kennerud, Skyrider", cost = "{2}{W}{U}{U}", type = "Legendary Creature — Human Knight", pt = "3/3",
        text = """
            Flying
            Whenever you cast an instant or sorcery spell, create a 1/1 white Soldier creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(or(instant, sorcery)), create(soldier)),
    )

    // CR 111.2, 603.2: tokens for each creature with defender, and an enters trigger for later ones
    @Test
    fun wingmantlePriest() = card(
        name = "Wingmantle Priest", cost = "{3}{W}", type = "Creature — Human Cleric", pt = "0/3",
        text = """
            Defender
            When this creature enters, create a 1/1 white Bird creature token with flying for each creature with defender you control.
            Whenever another creature you control with defender enters, create a 1/1 white Bird creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf, create(birdToken, count(and(creature, kw(Keyword.Defender), you)))),
        triggered(enters(and(another, creature, you, kw(Keyword.Defender))), create(birdToken)),
    )
}
