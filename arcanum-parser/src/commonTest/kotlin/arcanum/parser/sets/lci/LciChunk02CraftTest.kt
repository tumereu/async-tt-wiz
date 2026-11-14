package arcanum.parser.sets.lci

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.CostObjects
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Ability
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.Layout
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 02: craft (CR 702.167) and the cards around it. */
class LciChunk02CraftTest {
    private fun craft(printed: String, cost: String, vararg materials: CostObjects.Choose) = Keyword.Craft(materials.toList(), manaCost(cost), printed)
    private fun one(filter: ObjectFilter) = CostObjects.Choose(filter, n(1))
    private fun reminder(cost: String, materials: String) =
        "($cost, Exile this artifact, Exile $materials: Return this card transformed under its owner's control. Craft only as a sorcery.)"
    private val net = CounterKind.Named("net")
    private val crafted = Objects.LinkedTo(Keyword.Craft.Kind)
    private val golem = creatureToken("Golem", listOf(Color.White, Color.Blue), 4, 4, types = listOf(CardType.Artifact, CardType.Creature))
    private val anotherArtifact = "another artifact you control or an artifact card from your graveyard"
    private fun eachOpponentSacrifices(filter: ObjectFilter) = Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
        Step.Choose(Choice.Objects(and(filter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
            bind = "sacrificed", chooser = Players.Bound("opponent")),
        doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)

    // CR 702.167a, b: "creature" is a creature you control or a creature card in your graveyard
    @Test
    fun tributeKnife() = cardFaces(Layout.Transform,
        card(
            name = "Tribute Knife",
            cost = "{1}{B}",
            type = "Artifact",
            text = """
                When this artifact enters, each opponent sacrifices a creature of their choice.
                Craft with creature {4}{B} ${reminder("{4}{B}", "a creature you control or a creature card from your graveyard")}
            """,
        ) to abilities(
            triggered(entersSelf, eachOpponentSacrifices(creature)),
            craft("creature", "{4}{B}", one(creature)),
        ),
        card(
            name = "Devouring Crypt",
            cost = null,
            type = "Artifact",
            text = "At the beginning of your upkeep, each opponent loses 1 life and you gain 1 life.",
        ) to abilities(
            triggered(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
        ),
    )

    // CR 702.167a: two materials of one kind
    @Test
    fun maskOfTerror() = cardFaces(Layout.Transform,
        card(
            name = "Mask of Terror",
            cost = "{1}{B}",
            type = "Artifact",
            text = """
                When this artifact enters, target opponent reveals their hand. You choose an artifact or creature card from it. That player discards that card.
                Craft with two creatures {5}{B} ${reminder("{5}{B}", "the two from among creatures you control and/or creature cards in your graveyard")}
            """,
        ) to abilities(
            triggered(entersSelf,
                doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
                Step.Choose(Choice.Objects(or(artifact, creature), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent)),
            craft("two creatures", "{5}{B}", CostObjects.Choose(creature, n(2))),
        ),
        card(
            name = "Terror Bonebeast",
            cost = null,
            type = "Creature — Dinosaur Skeleton Horror",
            pt = "5/4",
            text = """
                Menace
                Whenever this creature enters or attacks, you may mill two cards. (You may put the top two cards of your library into your graveyard.)
            """,
        ) to abilities(
            Keyword.Menace,
            triggered(entersOrAttacksSelf, Step.May(listOf(mill(2)), Players.You)),
        ),
    )

    // CR 702.167a, b: a subtype without "card": an Island you control or an Island card in your graveyard
    @Test
    fun soddenWreck() = cardFaces(Layout.Transform,
        card(
            name = "Sodden Wreck",
            cost = "{U}",
            type = "Artifact",
            text = """
                {T}: Mill a card. (Put the top card of your library into your graveyard.)
                Craft with Island {3}{U} ${reminder("{3}{U}", "an Island you control or an Island card from your graveyard")}
            """,
        ) to abilities(
            activated(cost(tap), mill(1)),
            craft("Island", "{3}{U}", one(sub("Island"))),
        ),
        card(
            name = "Sealed Skiff",
            cost = null,
            type = "Artifact — Vehicle",
            pt = "4/4",
            text = """
                Vigilance
                Descend 8 — This Vehicle can't be blocked as long as there are eight or more permanent cards in your graveyard.
                Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
            """,
        ) to abilities(
            Keyword.Vigilance,
            // CR 700.11 (descend 8 is an ability word): eight or more permanent cards in your graveyard
            static(Mechanic.CantBeBlocked, condition = Condition.Exists(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 8))
                .copy(abilityWord = "Descend 8"),
            Keyword.Crew(1),
        ),
    )

    // CR 702.167a: the artifact the craft exiles is "another artifact"; the back face's creatures-you-control anthem (CR 613.4c)
    @Test
    fun kilnBakedTiles() = cardFaces(Layout.Transform,
        card(
            name = "Kiln-Baked Tiles",
            cost = "{1}{W}",
            type = "Artifact",
            text = """
                When this artifact enters, search your library for a basic Plains card, reveal it, put it into your hand, then shuffle. You gain 2 life.
                Craft with artifact {5}{W}{W} ${reminder("{5}{W}{W}", anotherArtifact)}
            """,
        ) to abilities(
            triggered(entersSelf, search(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand),
                shuffle, gainLife(2)),
            craft("artifact", "{5}{W}{W}", one(artifact)),
        ),
        card(
            name = "Starmetal Furnace",
            cost = null,
            type = "Artifact",
            text = """
                When this artifact enters, create two 1/1 colorless Gnome artifact creature tokens.
                Creatures you control get +1/+1.
            """,
        ) to abilities(
            triggered(entersSelf, create(creatureToken("Gnome", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature)), n(2))),
            static(pt(1, 1), affects = Objects.Each(creatureYouControl)),
        ),
    )

    // CR 702.167a; CR 301.5c: the back face's Equip
    @Test
    fun grimChainwhip() = cardFaces(Layout.Transform,
        card(
            name = "Grim Chainwhip",
            cost = "{R}",
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +2/+0.
                Equip {1}
                Craft with artifact {3}{R}{R} ${reminder("{3}{R}{R}", anotherArtifact)}
            """,
        ) to abilities(
            static(pt(2, 0), affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(manaCost("{1}")),
            craft("artifact", "{3}{R}{R}", one(artifact)),
        ),
        card(
            name = "Grim Hand Cannon",
            cost = null,
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +3/+0 and has "Whenever this creature attacks, you may sacrifice an artifact other than Grim Hand Cannon. When you do, this creature deals damage equal to its power to target creature."
                Equip {1}
            """,
        ) to abilities(
            static(pt(3, 0), has(triggered(attacksSelf,
                Step.Pay(cost(sacrificeA(and(artifact, not(ObjectFilter.Is(Objects.Granter))))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(power(Objects.Self), toObjects(target("creature"))), targets = listOf(slot("creature", creature))))))),
                affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(manaCost("{1}")),
        ),
    )

    // CR 702.167a; CR 301.5c: "attach it to target creature you control" as the Equipment enters
    @Test
    fun effigyOfTheSunkenKing() = cardFaces(Layout.Transform,
        card(
            name = "Effigy of the Sunken King",
            cost = "{2}{R}",
            type = "Artifact",
            text = """
                Flash
                When this artifact enters, it deals 2 damage to any target.
                Craft with artifact {2}{R} ${reminder("{2}{R}", anotherArtifact)}
            """,
        ) to abilities(
            Keyword.Flash,
            triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget)),
            craft("artifact", "{2}{R}", one(artifact)),
        ),
        card(
            name = "Monarch's Obsidian Blade",
            cost = null,
            type = "Artifact — Equipment",
            text = """
                When this Equipment enters, attach it to target creature you control.
                Equipped creature gets +2/+0.
                Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
            """,
        ) to abilities(
            triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
            static(pt(2, 0), affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(manaCost("{2}")),
        ),
    )

    // CR 702.167a; CR 701.26a: "tap or untap" is a choice on resolution
    @Test
    fun upturnedFloe() = cardFaces(Layout.Transform,
        card(
            name = "Upturned Floe",
            cost = "{1}{U}",
            type = "Artifact",
            text = """
                When this artifact enters, mill a card, then draw a card. (To mill a card, put the top card of your library into your graveyard.)
                Craft with artifact {4}{U}{U} ${reminder("{4}{U}{U}", anotherArtifact)}
            """,
        ) to abilities(
            triggered(entersSelf, mill(1), draw(1)),
            craft("artifact", "{4}{U}{U}", one(artifact)),
        ),
        card(
            name = "Floe Colossus",
            cost = null,
            type = "Artifact Creature — Golem",
            pt = "6/6",
            text = "Whenever this creature attacks, you may tap or untap target artifact or creature.",
        ) to abilities(
            triggered(attacksSelf,
                Step.May(listOf(
                    Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                    Step.If(Condition.Chose("tapOrUntap", "tap"), then = listOf(tapIt(target("permanent"))), otherwise = listOf(untapIt(target("permanent"))))), Players.You),
                targets = listOf(slot("permanent", or(artifact, creature)))),
        ),
    )

    // CR 702.167a, c: "creature" materials; the back face reads the exiled card's mana value
    @Test
    fun verdantSeedgems() = cardFaces(Layout.Transform,
        card(
            name = "Verdant Seedgems",
            cost = "{3}{G}",
            type = "Artifact",
            text = """
                When this artifact enters, distribute three +1/+1 counters among one, two, or three target creatures you control.
                Craft with creature {5}{G}{G} ${reminder("{5}{G}{G}", "a creature you control or a creature card from your graveyard")}
            """,
        ) to abilities(
            Ability.Triggered(entersSelf, arcanum.api.Instructions(
                targets = listOf(arcanum.api.TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(1), max = n(3), divided = n(3))),
                steps = listOf(doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare))))),
            craft("creature", "{5}{G}{G}", one(creature)),
        ),
        card(
            name = "Verdantheart Warden",
            cost = null,
            type = "Artifact Creature — Golem",
            pt = "7/7",
            text = "When this creature enters, you gain life equal to the mana value of the exiled card used to craft it.",
        ) to abilities(
            triggered(entersSelf, doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.ManaValue, crafted, Combine.Single)))),
        ),
    )

    // CR 702.167a, b: "Cave" is a Cave you control or a Cave card in your graveyard
    @Test
    fun ixtalsRootstone() = cardFaces(Layout.Transform,
        card(
            name = "Ixtal's Rootstone",
            cost = "{2}{G}",
            type = "Artifact",
            text = """
                When this artifact enters, look at the top six cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest on the bottom in a random order.
                Craft with Cave {5}{G} ${reminder("{5}{G}", "a Cave you control or a Cave card from your graveyard")}
            """,
        ) to abilities(
            triggered(entersSelf,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
            craft("Cave", "{5}{G}", one(sub("Cave"))),
        ),
        card(name = "Ixtal's Walker", cost = null, type = "Artifact Creature — Golem", pt = "5/5") to abilities(),
    )

    // CR 702.167a; CR 122.1d: stun counters; CR 701.44a: explore
    @Test
    fun magnetPin() = cardFaces(Layout.Transform,
        card(
            name = "Magnet Pin",
            cost = "{1}{U}",
            type = "Artifact",
            text = """
                Flash
                When this artifact enters, tap up to one target artifact or creature and put two stun counters on it.
                Craft with artifact {2}{U} ${reminder("{2}{U}", anotherArtifact)}
            """,
        ) to abilities(
            Keyword.Flash,
            triggered(entersSelf, tapIt(target("permanent")), counters(target("permanent"), 2, CounterKind.Stun),
                targets = listOf(slot("permanent", or(artifact, creature), min = 0))),
            craft("artifact", "{2}{U}", one(artifact)),
        ),
        card(
            name = "Wayfinder Dial",
            cost = null,
            type = "Artifact",
            text = "{1}, {T}: Target creature you control explores. Activate only as a sorcery. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard.)",
        ) to abilities(
            activated(cost(mana("{1}"), tap), doing(Effect.Action(KeywordAction.Explore(target("creature")))), targets = listOf(slot("creature", creatureYouControl)),
                rules = asSorcery),
        ),
    )

    // CR 702.167a; the back face's activation condition: any artifact (this one included) entered under your control this turn
    @Test
    fun artisansWallPlan() = cardFaces(Layout.Transform,
        card(
            name = "Artisan's Wall-Plan",
            cost = "{3}{W}{U}",
            type = "Artifact",
            text = """
                When this artifact enters, create a 4/4 white and blue Golem artifact creature token.
                Craft with artifact {4}{W}{W}{U} ${reminder("{4}{W}{W}{U}", anotherArtifact)}
            """,
        ) to abilities(
            triggered(entersSelf, create(golem)),
            craft("artifact", "{4}{W}{W}{U}", one(artifact)),
        ),
        card(
            name = "Artisan's Workshop",
            cost = null,
            type = "Artifact",
            text = "{T}: Create a 4/4 white and blue Golem artifact creature token. Activate only if this artifact or another artifact entered the battlefield under your control this turn.",
        ) to abilities(
            activated(cost(tap), create(golem), rules = anyTime.copy(condition = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events))),
        ),
    )

    // CR 702.167a; CR 702.9a: flying given until end of turn
    @Test
    fun skyTempleMarker() = cardFaces(Layout.Transform,
        card(
            name = "Sky Temple Marker",
            cost = "{W}",
            type = "Artifact",
            text = """
                When this artifact enters, scry 2.
                Craft with artifact {2}{W} ${reminder("{2}{W}", anotherArtifact)}
            """,
        ) to abilities(
            triggered(entersSelf, scry(2)),
            craft("artifact", "{2}{W}", one(artifact)),
        ),
        card(
            name = "Sky Temple Floater",
            cost = null,
            type = "Artifact Creature — Golem",
            pt = "1/4",
            text = """
                Flying
                Whenever this creature attacks, target attacking creature without flying gains flying until end of turn.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(attacksSelf, gains(target("creature"), Keyword.Flying),
                targets = listOf(slot("creature", and(attacking, creature, not(kw(Keyword.Flying)))))),
        ),
    )

    // CR 702.167a, c: "one or more Dinosaurs"; the back face's power is the exiled cards' total power (CR 604.3)
    @Test
    fun tinkerersFrame() = cardFaces(Layout.Transform,
        card(
            name = "Tinkerer's Frame",
            cost = "{1}{R}",
            type = "Artifact",
            text = """
                When this artifact enters, you may discard a card. If you do, draw two cards.
                Craft with one or more Dinosaurs {4}{R} ${reminder("{4}{R}", "one or more Dinosaurs you control and/or Dinosaur cards from your graveyard")}
            """,
        ) to abilities(
            triggered(entersSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
            craft("one or more Dinosaurs", "{4}{R}", CostObjects.Choose(sub("Dinosaur"), null, atLeast = 1)),
        ),
        card(
            name = "Masterwork Runner",
            cost = null,
            type = "Artifact Creature — Dinosaur",
            pt = "*/4",
            text = "Masterwork Runner's power is equal to the total power of the exiled cards used to craft it.",
        ) to abilities(
            static(Mechanic.SetPowerToughness(Value.Of(NumericProperty.Power, crafted, Combine.Total), null), characteristicDefining = true),
        ),
    )

    // CR 702.167a; CR 702.122a: crew, and a second way to animate it
    @Test
    fun coiledBladetrap() = cardFaces(Layout.Transform,
        card(
            name = "Coiled Bladetrap",
            cost = "{1}{W}",
            type = "Artifact",
            text = """
                Flash
                When this artifact enters, it deals 5 damage to target tapped creature an opponent controls.
                Craft with artifact {3}{W} ${reminder("{3}{W}", anotherArtifact)}
            """,
        ) to abilities(
            Keyword.Flash,
            triggered(entersSelf, damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", and(tapped, creature, opponents)))),
            craft("artifact", "{3}{W}", one(artifact)),
        ),
        card(
            name = "Bladewheel Cart",
            cost = null,
            type = "Artifact — Vehicle",
            pt = "5/5",
            text = """
                Tap two other untapped artifacts you control: This Vehicle becomes an artifact creature until end of turn.
                Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
            """,
        ) to abilities(
            activated(cost(CostPart.TapObjects(CostObjects.Choose(and(another, artifact), n(2)))),
                untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))),
            Keyword.Crew(1),
        ),
    )

    // CR 702.167a, c: "one or more" of anything; the back face's P/T and mana read the colors among the exiled cards (CR 105.4)
    @Test
    fun dawnbirdBanner() = cardFaces(Layout.Transform,
        card(
            name = "Dawnbird Banner",
            cost = "{3}",
            type = "Artifact",
            text = """
                {T}: Add one mana of any color.
                Craft with one or more {5} ${reminder("{5}", "one or more other permanents you control and/or cards from your graveyard")}
            """,
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
            craft("one or more", "{5}", CostObjects.Choose(ObjectFilter.Any, null, atLeast = 1)),
        ),
        card(
            name = "Dawnbird Totem",
            cost = null,
            type = "Artifact Creature — Bird Construct",
            pt = "*/*",
            text = """
                Flying, vigilance, haste
                Dawnbird Totem's power and toughness are each equal to the number of colors among the exiled cards used to craft it.
                {T}: For each color among the exiled cards used to craft this creature, add one mana of that color.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Vigilance,
            Keyword.Haste,
            static(Mechanic.SetPowerToughness(Value.CountDistinct(crafted, SharedQuality.Color), Value.CountDistinct(crafted, SharedQuality.Color)),
                characteristicDefining = true),
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.EachColorAmong(crafted)), Players.You))),
        ),
    )

    // CR 614.1c: enters with counters; CR 611.2b: the restriction lasts only while the permanent stays tapped; CR 702.167a: craft
    // without reminder text
    @Test
    fun wovenSnare() = cardFaces(Layout.Transform,
        card(
            name = "Woven Snare",
            cost = "{2}{U}",
            type = "Artifact",
            text = """
                This artifact enters with three net counters on it.
                {T}, Remove a net counter from this artifact: Tap another target nonland permanent. Its activated abilities can't be activated for as long as it remains tapped.
                Craft with artifact {1}{U}
            """,
        ) to abilities(
            static(Mechanic.Replacement(
                event = arcanum.api.EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(arcanum.api.Modification.EntersWithCounters(net, amount = n(3))))),
            activated(cost(tap, CostPart.RemoveCounters(net, n(1), CostObjects.Self)),
                tapIt(target("permanent")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CantBeActivated(manaAbilities = true)), arcanum.api.Affected.Objects(target("permanent")),
                    arcanum.api.Duration.AsLongAs(Condition.ObjectMatches(target("permanent"), tapped)))),
                targets = listOf(slot("permanent", and(another, nonland, permanentFilter)))),
            craft("artifact", "{1}{U}", one(artifact)),
        ),
        card(
            name = "Woven Knot",
            cost = null,
            type = "Artifact",
            text = "{3}{U}, {T}: Draw a card for each artifact you control, then put this artifact into its owner's library third from the top.",
        ) to abilities(
            activated(cost(mana("{3}{U}"), tap), doing(Effect.Draw(Players.You, count(and(artifact, you)))),
                doing(Effect.Move(Objects.Self, Destination(Zone.Library, libraryPosition = 2)))),
        ),
    )

    // CR 702.167a: four materials of four kinds; CR 702.167c, 508.4: an exiled creature card used to craft it enters tapped and attacking
    @Test
    fun seatOfTheDreadAdmiral() = cardFaces(Layout.Transform,
        card(
            name = "Seat of the Dread Admiral",
            cost = "{2}",
            type = "Legendary Artifact",
            text = """
                {T}: Mill two cards.
                Craft with a Dinosaur, a Merfolk, a Pirate, and a Vampire {4} ${reminder("{4}", "the four from among permanents you control and/or cards in your graveyard")}
            """,
        ) to abilities(
            activated(cost(tap), mill(2)),
            craft("a Dinosaur, a Merfolk, a Pirate, and a Vampire", "{4}", one(sub("Dinosaur")), one(sub("Merfolk")), one(sub("Pirate")), one(sub("Vampire"))),
        ),
        card(
            name = "The Dread Admiral",
            cost = null,
            type = "Legendary Creature — Skeleton Spirit Pirate",
            pt = "7/7",
            text = """
                Menace, trample, lifelink, hexproof
                Whenever The Dread Admiral attacks, each opponent sacrifices a nonland permanent of their choice. Then you may put an exiled creature card used to craft The Dread Admiral onto the battlefield under your control tapped and attacking.
            """,
        ) to abilities(
            Keyword.Menace,
            Keyword.Trample,
            Keyword.Lifelink,
            Keyword.Hexproof,
            triggered(attacksSelf,
                eachOpponentSacrifices(and(nonland, permanentFilter)),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = crafted), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, controller = Players.You,
                    attacking = arcanum.api.AttackTarget.Chosen)))),
        ),
    )

    // CR 702.167a, c: four or more nonlands with activated abilities; layer 6 (CR 613.1f): their activated abilities, each once a turn
    // (CR 602.5b); CR 707.10: a copy of each non-mana ability you activate (CR 605.1a)
    @Test
    fun theRiddleGem() = cardFaces(Layout.Transform,
        card(
            name = "The Riddle Gem",
            cost = "{U}",
            type = "Legendary Artifact",
            text = """
                The Riddle Gem enters tapped.
                {T}: Add {C}{C}. Spend this mana only to activate abilities.
                Craft with four or more nonlands with activated abilities {8}{U} ${reminder("{8}{U}", "the four or more from among other permanents you control and/or cards in your graveyard")}
            """,
        ) to abilities(
            entersTapped,
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!), restriction = ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated)), Players.You))),
            craft("four or more nonlands with activated abilities", "{8}{U}",
                CostObjects.Choose(and(nonland, ObjectFilter.HasAbilityKind(arcanum.api.AbilityKind.Activated)), null, atLeast = 4)),
        ),
        card(
            name = "Nexus of Insight",
            cost = null,
            type = "Legendary Artifact",
            text = """
                Nexus of Insight has each activated ability of the exiled cards used to craft it. You may activate each of those abilities only once each turn.
                Whenever you activate an ability that isn't a mana ability, copy it. You may choose new targets for the copy.
            """,
        ) to abilities(
            static(Mechanic.GainAbilitiesOf(crafted, perTurn = 1)),
            triggered(on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You)),
                doing(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), Players.You, newTargets = true))),
        ),
    )

    // CR 702.167a: "one or more" of a subtype; "one or more" of anything (other permanents you control and/or cards in your graveyard)
    @Test
    fun craftMaterialCounts() = card(
        name = "Dawnbird Banner",
        cost = "{3}",
        type = "Artifact",
        text = """
            Craft with one or more {5} ${reminder("{5}", "one or more other permanents you control and/or cards from your graveyard")}
            Craft with one or more Dinosaurs {4}{R}
            Craft with four or more nonlands with activated abilities {8}{U}
            Craft with a Dinosaur, a Merfolk, a Pirate, and a Vampire {4}
        """,
    ) parsesTo abilities(
        craft("one or more", "{5}", CostObjects.Choose(ObjectFilter.Any, null, atLeast = 1)),
        craft("one or more Dinosaurs", "{4}{R}", CostObjects.Choose(sub("Dinosaur"), null, atLeast = 1)),
        craft("four or more nonlands with activated abilities", "{8}{U}",
            CostObjects.Choose(and(nonland, ObjectFilter.HasAbilityKind(arcanum.api.AbilityKind.Activated)), null, atLeast = 4)),
        craft("a Dinosaur, a Merfolk, a Pirate, and a Vampire", "{4}", one(sub("Dinosaur")), one(sub("Merfolk")), one(sub("Pirate")), one(sub("Vampire"))),
    )
}
