package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Chunk 2: transforming Sagas (CR 714, 712): chapter abilities, then the back face after "III — Exile this Saga, then return it transformed". */
class NeoChunk02TransformTest {
    private val sagaReminder = "(As this Saga enters and after your draw step, add a lore counter.)"
    private val chapterThree = "III — Exile this Saga, then return it to the battlefield transformed under your control."
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun front(name: String, cost: String, vararg chapters: String) =
        card(name = name, cost = cost, type = "Enchantment — Saga", text = (listOf(sagaReminder) + chapters + chapterThree).joinToString("\n"))
    private fun back(name: String, type: String, pt: String, color: String, vararg lines: String) =
        card(name = name, cost = null, type = type, text = lines.joinToString("\n"), pt = pt, colorIndicator = color)
    private fun pumpX(objects: Objects, x: Value) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(x, n(0))),
        Affected.Objects(objects), Duration.EndOfTurn))

    // CR 714.2b (chapter abilities), 608.2c ("that many": the number discarded), 707.9a (a copy with haste), 603.7 (the end step sacrifice)
    @Test
    fun fableOfTheShatteredGlass() = cardFaces(Layout.Transform,
        front("Fable of the Shattered Glass", "{2}{R}",
            "I — Create a 2/2 red Goblin Shaman creature token with \"Whenever this token attacks, create a Treasure token.\"",
            "II — You may discard up to two cards. If you do, draw that many cards.",
        ) to abilities(
            chapter(1, create(creatureToken("Goblin Shaman", listOf(Color.Red), 2, 2, abilities = listOf(triggered(attacksSelf, createTreasure()))))),
            chapter(2,
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(2)), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.You, n(2), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
                doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("discarded"))))),
            exileAndReturnTransformed,
        ),
        back("Reflection of Kiriko", "Enchantment Creature — Goblin Shaman", "2/2", "R",
            "{1}, {T}: Create a token that's a copy of another target nonlegendary creature you control, except it has haste. Sacrifice it at the beginning of the next end step.",
        ) to abilities(
            activated(cost(mana("{1}"), tap),
                doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature"), listOf(CopyException.AddAbilities(listOf(Keyword.Haste)))), n(1), Players.You, bind = "tokens")),
                delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("tokens"), by = Players.You))),
                targets = listOf(slot("creature", and(another, not(legendary), creature, you)))),
        ),
    )

    // CR 120.3, 104.3e (lose the game), 603.4 (an intervening "if": damage this creature dealt that player this turn, added up)
    @Test
    fun gorebellyConsumesAll() = cardFaces(Layout.Transform,
        front("Gorebelly Consumes All", "{1}{B}{R}",
            "I — Destroy each nonland permanent with mana value 1 or less.",
            "II — Exile all graveyards.",
        ) to abilities(
            chapter(1, destroy(Objects.Each(and(nonland, permanentFilter, manaValue(Comparison.AtMost, 1))))),
            chapter(2, move(Objects.Each(inZone(Zone.Graveyard)), Zone.Exile)),
            exileAndReturnTransformed,
        ),
        back("Vessel of the Devouring", "Enchantment Creature — Ogre Shaman", "3/3", "BR",
            "Trample",
            "Whenever this creature deals damage, put a +1/+1 counter on it.",
            "Whenever this creature deals damage to a player, if it has dealt 10 or more damage to that player this turn, they lose the game.",
        ) to abilities(
            Keyword.Trample,
            triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(), per = DamageOccurrence.Source)), counters(Objects.Self)),
            triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any))),
                doing(Effect.LoseGame(Players.EventPlayer)),
                interveningIf = Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self),
                    RecipientFilter(players = PlayerFilter.Is(Players.EventPlayer))), atLeast = 10, measure = Measure.Amount)),
        ),
    )

    // CR 601.2e, 101.2 (a can't over a can), 608.2h (that spell's mana value as the ability resolves), 611.2b (until your next turn)
    @Test
    fun cleverIteration() = cardFaces(Layout.Transform,
        front("Clever Iteration", "{3}{U}",
            "I — Return up to one target creature or planeswalker to its owner's hand.",
            "II — Return an artifact card from your graveyard to your hand. If you can't, draw a card.",
        ) to abilities(
            chapter(1, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", or(creature, planeswalker), min = 0))),
            chapter(2,
                Step.Choose(Choice.Objects(inYourGraveyard(artifact), n(1), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand),
                ifThen(Condition.Not(Condition.NonEmpty("chosen")), draw(1))),
            exileAndReturnTransformed,
        ),
        back("Living Epiphany", "Enchantment Creature — Moonfolk", "3/3", "U",
            "Flying",
            "Whenever you cast a spell, your opponents can't cast spells with the same mana value as that spell until your next turn.",
        ) to abilities(
            Keyword.Flying,
            triggered(youCast(ObjectFilter.Any),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CantCast(and(spellFilter, ObjectFilter.ManaValue(Comparison.Equal,
                    Value.Of(NumericProperty.ManaValue, thatSpell, Combine.Single))))), Affected.Players(Players.Each(PlayerFilter.Opponent)),
                    Duration.UntilYourNextTurn))),
        ),
    )

    // CR 120.3 (damage to players and planeswalkers), 614.1a (a "would die ... instead" replacement), 700.4 (dies)
    @Test
    fun kumanoFacesTheFlame() = cardFaces(Layout.Transform,
        front("Kumano Faces the Flame", "{R}",
            "I — This Saga deals 1 damage to each opponent and each planeswalker they control.",
            "II — When you next cast a creature spell this turn, that creature enters with an additional +1/+1 counter on it.",
        ) to abilities(
            chapter(1, damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent), objects = Objects.Each(and(planeswalker, opponents))))),
            chapter(2, doing(Effect.CreateDelayedTrigger(youCast(and(creature, spellFilter)), Instructions(steps = listOf(
                doing(Effect.SpellGets(thatSpell, listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))))))), Duration.EndOfTurn))),
            exileAndReturnTransformed,
        ),
        back("Etching of the Flame", "Enchantment Creature — Human Shaman", "2/2", "R",
            "Haste",
            "If a creature dealt damage this turn by a source you controlled would die, exile it instead.",
        ) to abilities(
            Keyword.Haste,
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(you, RecipientFilter()),
                role = EventRole.Object)), from = Zone.Battlefield, to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
                affects = Affected.Objects(Objects.Self)),
        ),
    )

    // CR 700.2 (a modal chapter ability), 714.2b; CR 106.6 (mana spendable only on instants and sorceries)
    @Test
    fun lifeOfToshikoUmezawa() = cardFaces(Layout.Transform,
        front("Life of Toshiko Umezawa", "{1}{B}",
            "I, II — Choose one —",
            "• Target creature gets +2/+2 until end of turn.",
            "• Target creature gets -1/-1 until end of turn.",
            "• You gain 2 life.",
        ) to abilities(
            Ability.Triggered(Trigger.Chapter(listOf(1, 2)), Instructions(modes = Modes(listOf(
                Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(target("creature"), 2, 2)))),
                Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(target("creature"), -1, -1)))),
                Mode(Instructions(steps = listOf(gainLife(2)))),
            ), min = 1, max = 1, chooser = Players.You))),
            exileAndReturnTransformed,
        ),
        back("Memory of Toshiko", "Enchantment Creature — Human Samurai", "2/3", "B",
            "{T}, Pay 1 life: Add {B}. Spend this mana only to cast an instant or sorcery spell.",
        ) to abilities(
            activated(cost(tap, CostPart.PayLife(n(1))), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!),
                restriction = and(spellFilter, or(instant, sorcery))), Players.You))),
        ),
    )

    // CR 613.1b (control changes), 611.2c (the permanents affected are fixed as it resolves), 108.3 (owner)
    @Test
    fun theFallOfLordKondo() = cardFaces(Layout.Transform,
        front("The Fall of Lord Kondo", "{2}{W}",
            "I — Exile target creature an opponent controls with mana value 4 or greater.",
            "II — Each player gains control of all permanents they own.",
        ) to abilities(
            chapter(1, move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(creature, opponents, manaValue(Comparison.AtLeast, 4))))),
            chapter(2, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("player"))),
                    Affected.Objects(Objects.Each(and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))))), Duration.Indefinitely))))),
            exileAndReturnTransformed,
        ),
        back("Fragment of Kondo", "Enchantment Creature — Human Noble", "1/3", "W",
            "Defender",
            "When this creature dies, draw a card.",
        ) to abilities(
            Keyword.Defender,
            triggered(diesSelf, draw(1)),
        ),
    )

    // CR 105.2 (all colors), 506.2 (defending player), 608.2d (that player chooses), 608.2h (X fixed as it resolves)
    @Test
    fun theKamiConflict() = cardFaces(Layout.Transform,
        front("The Kami Conflict", "{1}{W}{U}{B}{R}{G}",
            "I — Exile target nonland permanent an opponent controls.",
            "II — Return up to one other target nonland permanent to its owner's hand. Then each opponent discards a card.",
        ) to abilities(
            chapter(1, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
            chapter(2, move(target("permanent"), Zone.Hand), discard(1, Players.Each(PlayerFilter.Opponent)),
                targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0))),
            exileAndReturnTransformed,
        ),
        back("O-Kagami Made Manifest", "Enchantment Creature — Dragon Spirit", "6/6", "WUBRG",
            "O-Kagami Made Manifest is all colors.",
            "Flying, trample",
            "Whenever this creature attacks, defending player chooses a nonland card in your graveyard. Return that card to your hand. This creature gets +X/+0 until end of turn, where X is the mana value of that card.",
        ) to abilities(
            static(Mechanic.SetColors(listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green).map { ColorRef.Literal(it) }), characteristicDefining = true),
            Keyword.Flying,
            Keyword.Trample,
            triggered(attacksSelf,
                Step.Choose(Choice.Objects(inYourGraveyard(nonland), n(1), n(1)), bind = "chosen",
                    chooser = Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object))),
                move(Objects.Bound("chosen"), Zone.Hand),
                pumpX(Objects.Self, Value.Of(NumericProperty.ManaValue, Objects.Bound("chosen"), Combine.Single))),
        ),
    )

    // CR 118.12a ("unless they discard": each opponent in turn order, CR 101.4), 701.21a; CR 506.2 (defending player's graveyard)
    @Test
    fun theLongGraspOfNight() = cardFaces(Layout.Transform,
        front("The Long Grasp of Night", "{3}{B}",
            "I, II — Each opponent sacrifices a creature of their choice unless they discard a card.",
        ) to abilities(
            chapters(listOf(1, 2), Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.Bound("opponent"), bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")),
                    Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                        bind = "sacrificed", chooser = Players.Bound("opponent")),
                    doing(Effect.Sacrifice(Objects.Bound("sacrificed"))))))),
            exileAndReturnTransformed,
        ),
        back("Animus of Night's Grasp", "Enchantment Creature — Spirit", "0/4", "B",
            "Menace (This creature can't be blocked except by two or more creatures.)",
            "Whenever this creature attacks, it gets +X/+0 until end of turn, where X is the number of creature cards in defending player's graveyard.",
        ) to abilities(
            Keyword.Menace,
            triggered(attacksSelf, pumpX(Objects.Self, count(and(creature, inZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
        ),
    )

    // CR 111.2 (each opponent creates the token: theirs), 608.2f (one event); CR 613.1b (gain control of all Rat tokens)
    @Test
    fun tributeToHorobo() = cardFaces(Layout.Transform,
        front("Tribute to Horobo", "{1}{B}",
            "I, II — Each opponent creates a 1/1 black Rat Rogue creature token.",
        ) to abilities(
            chapters(listOf(1, 2), doing(Effect.CreateToken(creatureToken("Rat Rogue", listOf(Color.Black), 1, 1), n(1), Players.Each(PlayerFilter.Opponent)))),
            exileAndReturnTransformed,
        ),
        back("Echo of Death's Lament", "Enchantment Creature — Spirit", "3/3", "B",
            "Flying, haste",
            "When this creature enters, gain control of all Rat tokens.",
            "Whenever this creature attacks, you may sacrifice another creature. If you do, draw a card.",
        ) to abilities(
            Keyword.Flying,
            Keyword.Haste,
            triggered(entersSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(Objects.Each(and(sub("Rat"), token))),
                Duration.Indefinitely))),
            triggered(attacksSelf,
                Step.Pay(cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), draw(1))),
        ),
    )

    // CR 305.2 (an additional land play), 509.1h (becomes blocked)
    @Test
    fun azukasManyJourneys() = cardFaces(Layout.Transform,
        front("Azuka's Many Journeys", "{1}{G}",
            "I — You may play an additional land this turn.",
            "II — You gain 3 life.",
        ) to abilities(
            chapter(1, doing(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn))),
            chapter(2, gainLife(3)),
            exileAndReturnTransformed,
        ),
        back("Likeness of the Wanderer", "Enchantment Creature — Human Monk", "3/3", "G",
            "Whenever this creature becomes blocked, untap up to three lands you control.",
        ) to abilities(
            triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
                Step.Choose(Choice.Objects(and(land, you), n(0), n(3)), bind = "untapped", chooser = Players.You),
                untapIt(Objects.Bound("untapped"))),
        ),
    )

    // CR 714.2b, 702.9 (flying until end of turn)
    @Test
    fun befriendingTheMothsOfDusk() = cardFaces(Layout.Transform,
        front("Befriending the Moths of Dusk", "{3}{W}",
            "I, II — Target creature you control gets +1/+1 and gains flying until end of turn.",
        ) to abilities(
            chapters(listOf(1, 2), pump(target("creature"), 1, 1, has(Keyword.Flying)), targets = listOf(slot("creature", creatureYouControl))),
            exileAndReturnTransformed,
        ),
        back("Imperial Dusk Moth", "Enchantment Creature — Insect", "2/4", "W", "Flying") to abilities(Keyword.Flying),
    )

    // CR 701.23 (search, reveal), 401.4 (top of library); CR 613.4c (+1/+1 for each land)
    @Test
    fun boseijuReachesUpward() = cardFaces(Layout.Transform,
        front("Boseiju Reaches Upward", "{3}{G}",
            "I — Search your library for up to two basic Forest cards, reveal them, put them into your hand, then shuffle.",
            "II — Put up to one target land card from your graveyard on top of your library.",
        ) to abilities(
            chapter(1, search(and(ObjectFilter.Super(Supertype.Basic), sub("Forest")), max = 2, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            chapter(2, move(target("card"), Zone.Library, position = 0), targets = listOf(slot("card", inYourGraveyard(land), min = 0))),
            exileAndReturnTransformed,
        ),
        back("Bough of Boseiju", "Enchantment Creature — Plant", "0/0", "G",
            "Reach",
            "This creature gets +1/+1 for each land you control.",
        ) to abilities(
            Keyword.Reach,
            static(Mechanic.ModifyPowerToughness(count(and(land, you)), count(and(land, you)))),
        ),
    )

    // CR 107.3f (pay {X}: X chosen as it is paid), 603.12 (a reflexive trigger), 700.9 (modified creatures)
    @Test
    fun juganGuardsTheTemple() = cardFaces(Layout.Transform,
        front("Jugan Guards the Temple", "{2}{G}",
            "I — Create a 1/1 green Human Monk creature token with \"{T}: Add {G}.\"",
            "II — Put a +1/+1 counter on each of up to two target creatures.",
        ) to abilities(
            chapter(1, create(creatureToken("Human Monk", listOf(Color.Green), 1, 1, abilities = listOf(addMana("{G}"))))),
            chapter(2, counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
            exileAndReturnTransformed,
        ),
        back("Remnant of the Risen Star", "Enchantment Creature — Dragon Spirit", "2/2", "G",
            "Flying",
            "Whenever another creature you control enters, you may pay {X}. When you do, put X +1/+1 counters on that creature.",
            "As long as you control five or more modified creatures, this creature gets +5/+5 and has trample. (Equipment, Auras you control, and counters are modifications.)",
        ) to abilities(
            Keyword.Flying,
            triggered(enters(and(another, creature, you)),
                Step.Choose(Choice.Number(n(0), null), bind = "X", chooser = Players.You),
                Step.Pay(cost(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X"))), payer = Players.You, mandatory = false, bind = "paid"),
                ifThen(Condition.Did("paid"), reflexive(counters(Objects.EventObject(EventRole.After), Value.Bound("X"))))),
            static(pt(5, 5), has(Keyword.Trample), condition = Condition.Exists(and(creature, you, ObjectFilter.Modified), atLeast = 5)),
        ),
    )

    // CR 613.4c (until end of turn, counted as it resolves, CR 608.2h; the back face's bonus counted continuously)
    @Test
    fun michikosReignOfHonesty() = cardFaces(Layout.Transform,
        front("Michiko's Reign of Honesty", "{1}{W}",
            "I, II — Target creature gets +1/+1 until end of turn for each artifact and/or enchantment you control.",
        ) to abilities(
            chapters(listOf(1, 2), untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(artifactsAndEnchantments, artifactsAndEnchantments)),
                targets = listOf(slot("creature", creature))),
            exileAndReturnTransformed,
        ),
        back("Portrait of Michika", "Enchantment Creature — Human Noble", "0/0", "W",
            "This creature gets +1/+1 for each artifact and/or enchantment you control.",
        ) to abilities(
            static(Mechanic.ModifyPowerToughness(artifactsAndEnchantments, artifactsAndEnchantments)),
        ),
    )
    private val artifactsAndEnchantments = count(and(or(artifact, enchantment), you))

    // CR 119.3 (lose life), 702.110 (menace given to Vehicles you control)
    @Test
    fun okibaReckonerAssault() = cardFaces(Layout.Transform,
        front("Okiba Reckoner Assault", "{B}",
            "I, II — Each opponent loses 1 life and you gain 1 life.",
        ) to abilities(
            chapters(listOf(1, 2), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
            exileAndReturnTransformed,
        ),
        back("Nezumi Road Chief", "Enchantment Creature — Rat Rogue", "2/2", "B",
            "Menace",
            "Vehicles you control have menace. (They can't be blocked except by two or more creatures.)",
        ) to abilities(
            Keyword.Menace,
            static(has(Keyword.Menace), affects = Objects.Each(and(sub("Vehicle"), you))),
        ),
    )

    // CR 122.1a (+1/+1 counter), 702.20 (vigilance until end of turn; "it" is the target)
    @Test
    fun talesOfMasterSeshiru() = cardFaces(Layout.Transform,
        front("Tales of Master Seshiru", "{4}{G}",
            "I, II — Put a +1/+1 counter on target creature or Vehicle you control. It gains vigilance until end of turn.",
        ) to abilities(
            chapters(listOf(1, 2), counters(target("permanent")), gains(target("permanent"), Keyword.Vigilance),
                targets = listOf(slot("permanent", and(or(creature, sub("Vehicle")), you)))),
            exileAndReturnTransformed,
        ),
        back("Seshiru's Living Legacy", "Enchantment Creature — Snake Warrior", "5/5", "G", "Vigilance, haste") to
            abilities(Keyword.Vigilance, Keyword.Haste),
    )

    // CR 121.1, 701.9 (draw, then discard)
    @Test
    fun theModernEra() = cardFaces(Layout.Transform,
        front("The Modern Era", "{1}{U}", "I, II — Draw a card, then discard a card.") to abilities(
            chapters(listOf(1, 2), draw(1), discard(1)),
            exileAndReturnTransformed,
        ),
        back("Vector Skimmer", "Enchantment Creature — Spirit", "2/3", "U", "Flying") to abilities(Keyword.Flying),
    )

    // CR 603.12 (a reflexive trigger: the target chosen after the discard), 110.5b (enters tapped); CR 508.1 / 509.1 (attacks or blocks)
    @Test
    fun theRestorationOfEigan() = cardFaces(Layout.Transform,
        front("The Restoration of Eigan", "{2}{W}",
            "I — Search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.",
            "II — You may discard a card. When you do, return target permanent card with mana value 2 or less from your graveyard to the battlefield tapped.",
        ) to abilities(
            chapter(1, search(and(ObjectFilter.Super(Supertype.Basic), sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            chapter(2,
                Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), reflexive(move(target("card"), Zone.Battlefield, tapped = true),
                    targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(Comparison.AtMost, 2))))))),
            exileAndReturnTransformed,
        ),
        back("Architect of Renewal", "Enchantment Creature — Fox Monk", "3/4", "W",
            "Vigilance",
            "Whenever this creature attacks or blocks, create a 1/1 colorless Spirit creature token.",
        ) to abilities(
            Keyword.Vigilance,
            triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))),
                create(creatureToken("Spirit", emptyList(), 1, 1))),
        ),
    )

    // CR 613.1b (control until end of turn), 702.10 (haste); CR 613.4c (+1/+0 to creatures you control as it resolves, CR 611.2c)
    @Test
    fun theShatteredRealmsEra() = cardFaces(Layout.Transform,
        front("The Shattered Realms Era", "{4}{R}",
            "I — Gain control of target creature until end of turn. Untap it. It gains haste until end of turn.",
            "II — Creatures you control get +1/+0 until end of turn.",
        ) to abilities(
            chapter(1, untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)), untapIt(target("creature")), gains(target("creature"), Keyword.Haste),
                targets = listOf(slot("creature", creature))),
            chapter(2, pump(Objects.Each(creatureYouControl), 1, 0)),
            exileAndReturnTransformed,
        ),
        back("Nameless Vanquisher", "Enchantment Creature — Human Samurai", "3/3", "R", "Trample, haste") to
            abilities(Keyword.Trample, Keyword.Haste),
    )
}
