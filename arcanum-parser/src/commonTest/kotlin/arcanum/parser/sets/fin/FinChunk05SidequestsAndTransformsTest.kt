package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/**
 * Final Fantasy chunk 05: Sidequests and other double-faced cards that transform (CR 701.27, 712): transforming when a quest is done
 * (intervening ifs, CR 603.4), on the fourth resolution of an ability this turn, when the chosen creature leaves; "As this creature
 * transforms into …" (CR 712.20, applied while it transforms); an emblem (CR 114); "when the chosen player loses the game"; damage doubled
 * for Wizards (CR 614.1a); playing cards from the graveyard during your turn; cards put into your graveyard exiled instead.
 */
class FinChunk05SidequestsAndTransformsTest {
    private fun transformsInto(name: String) = EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name)))
    /** "As this creature transforms into [name], [steps]" (CR 712.20). */
    private fun asTransformsInto(name: String, vararg steps: Step) = static(Mechanic.Replacement(transformsInto(name), modify = listOf(Modification.AsTransforms(steps.toList()))))
    private val transformSelf = doing(Effect.Transform(Objects.Self))
    private val endOfCombatOnYourTurn = stepBegins(TurnStep.EndOfCombat, PlayerFilter.You)

    @Test
    fun questGoneFishing() = cardFaces(Layout.Transform,
        card(
            name = "Quest: Gone Fishing",
            cost = "{2}{W}",
            type = "Enchantment",
            text = """
                At the beginning of your upkeep, look at the top card of your library. If it's an artifact or creature card, you may reveal it and put it into your hand. If you put a card into your hand this way, create a Food token and transform this enchantment.
            """,
        ) to abilities(
            triggered(yourUpkeep,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "seen")),
                ifThen(Condition.ObjectMatches(Objects.Bound("seen"), or(artifact, creature)),
                    Step.Pay(steps = listOf(doing(Effect.Reveal(Objects.Bound("seen"))), move(Objects.Bound("seen"), Zone.Hand)), payer = Players.You, bind = "put")),
                ifThen(Condition.Did("put"), createFood, transformSelf)),
        ),
        card(
            name = "Riverside Camp",
            cost = null,
            type = "Land",
            colorIndicator = null,
            text = """
                {T}: Add {W}.
                {3}, {T}, Sacrifice an artifact: Put a +1/+1 counter on each creature you control. Activate only as a sorcery.
            """,
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}")!!)), Players.You))),
            activated(cost(mana("{3}"), tap, sacrificeA(artifact)), counters(Objects.Each(creatureYouControl)), rules = asSorcery),
        ),
    )

    @Test
    fun questCollectorsAlbum() = cardFaces(Layout.Transform,
        card(
            name = "Quest: Collector's Album",
            cost = "{3}{U}",
            type = "Enchantment",
            text = """
                When this enchantment enters, draw three cards, then discard two cards.
                At the beginning of your end step, if eight or more cards are in your graveyard, transform this enchantment.
            """,
        ) to abilities(
            triggered(entersSelf, draw(3), discard(2)),
            triggered(yourEndStep, transformSelf, interveningIf = Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 8)),
        ),
        card(
            name = "Enchanted Playing Card",
            cost = null,
            type = "Artifact — Vehicle",
            colorIndicator = "U",
            pt = "4/4",
            text = """
                Flying
                Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
            """,
        ) to abilities(Keyword.Flying, Keyword.Crew(1)),
    )

    @Test
    fun questBountyBoard() = cardFaces(Layout.Transform,
        card(
            name = "Quest: Bounty Board",
            cost = "{3}{B}{B}",
            type = "Enchantment",
            text = """
                When this enchantment enters, destroy up to one target creature.
                At the beginning of your end step, if a creature died under an opponent's control this turn, create a Treasure token. Then if you control three or more Treasures, transform this enchantment.
            """,
        ) to abilities(
            triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
            triggered(yourEndStep, createTreasure, ifThen(Condition.Exists(and(sub("Treasure"), you), atLeast = 3), transformSelf),
                interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(creature, opponents), from = Zone.Battlefield, to = Zone.Graveyard),
                    atLeast = 1, measure = Measure.Events)),
        ),
        card(
            name = "Varrok, Ultimate Quarry",
            cost = null,
            type = "Legendary Creature — Dragon",
            colorIndicator = "B",
            pt = "5/6",
            text = """
                {1}{B}, Sacrifice another creature or artifact: Varrok gains indestructible until end of turn. Tap it.
            """,
        ) to abilities(
            activated(cost(mana("{1}{B}"), sacrificeA(and(another, or(creature, artifact)))), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
        ),
    )

    /** "if a player was dealt 6 or more combat damage this turn": one player, their damage added up. */
    @Test
    fun questPlayTheMatch() = cardFaces(Layout.Transform,
        card(
            name = "Quest: Play the Match",
            cost = "{2}{R}",
            type = "Enchantment",
            text = """
                At the beginning of combat on your turn, target creature you control gets +2/+0 until end of turn.
                At the end of combat on your turn, if a player was dealt 6 or more combat damage this turn, transform this enchantment, then attach it to a creature you control.
            """,
        ) to abilities(
            triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 2, 0), targets = listOf(slot("creature", creatureYouControl))),
            triggered(endOfCombatOnYourTurn, transformSelf,
                Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
                doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("chosen")))),
                interveningIf = Condition.PlayerExists(PlayerFilter.Where(Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any,
                    RecipientFilter(players = PlayerFilter.Is(Players.Tested)), combat = true), atLeast = 6, measure = Measure.Amount)), atLeast = 1)),
        ),
        card(
            name = "Champion's Trophy, Celestial Arm",
            cost = null,
            type = "Legendary Artifact — Equipment",
            colorIndicator = "R",
            text = """
                Double Overdrive — Equipped creature gets +2/+0 and has double strike.
                Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
            """,
        ) to abilities(
            static(pt(2, 0), has(Keyword.DoubleStrike), affects = equipped).copy(abilityWord = "Double Overdrive"),
            Keyword.Equip(manaCost("{3}")),
        ),
    )

    @Test
    fun questRaiseARunner() = cardFaces(Layout.Transform,
        card(
            name = "Quest: Raise a Runner",
            cost = "{1}{G}",
            type = "Enchantment",
            text = """
                When this enchantment enters, create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
                At the beginning of your first main phase, if you control four or more Birds, transform this enchantment.
            """,
        ) to abilities(
            triggered(entersSelf, create(birdToken)),
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), transformSelf, interveningIf = Condition.Exists(and(sub("Bird"), you), atLeast = 4)),
        ),
        card(
            name = "Ebony Runner",
            cost = null,
            type = "Creature — Bird",
            colorIndicator = "G",
            pt = "2/2",
            text = """
                When this permanent transforms into Ebony Runner, search your library for a land card, put it onto the battlefield tapped, then shuffle.
                Landfall — Whenever a land you control enters, Birds you control get +1/+0 until end of turn.
            """,
        ) to abilities(
            triggered(on(transformsInto("Ebony Runner")), search(land), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
            triggered(landfall, pump(Objects.Each(and(sub("Bird"), you)), 1, 0), abilityWord = "Landfall"),
        ),
    )

    // CR 120.3 (damage dealt), 119.3 (half the starting life total), 701.27
    @Test
    fun cedricDarkKnight() = cardFaces(Layout.Transform,
        card(
            name = "Cedric, Dark Knight",
            cost = "{B}",
            type = "Legendary Creature — Human Knight",
            pt = "2/3",
            text = """
                Deathtouch
                Darkness — Whenever Cedric deals damage, you lose that much life. Then if your life total is less than or equal to half your starting life total, untap Cedric and transform it.
            """,
        ) to abilities(
            Keyword.Deathtouch,
            triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter())),
                doing(Effect.LoseLife(Players.You, Value.EventAmount)),
                ifThen(Condition.Compare(Value.LifeOf(Players.You), Comparison.AtMost, Value.Half(Value.StartingLife(Players.You), roundUp = false)),
                    untapIt(Objects.Self), transformSelf),
                abilityWord = "Darkness"),
        ),
        card(
            name = "Cedric, Redeemed Paladin",
            cost = null,
            type = "Legendary Creature — Human Knight",
            colorIndicator = "W",
            pt = "4/4",
            text = """
                Lifelink
                Protect — Whenever Cedric attacks, other attacking creatures gain indestructible until end of turn.
            """,
        ) to abilities(
            Keyword.Lifelink,
            triggered(attacksSelf, gains(Objects.Each(and(another, attacking, creature)), Keyword.Indestructible), abilityWord = "Protect"),
        ),
    )

    /** "If this is the fourth time this ability has resolved this turn" (counted with this resolution); an emblem made as it transforms. */
    // CR 114, 712.20
    @Test
    fun severusFabledSoldier() = cardFaces(Layout.Transform,
        card(
            name = "Severus, Fabled Soldier",
            cost = "{2}{B}",
            type = "Legendary Creature — Human Avatar Soldier",
            pt = "3/3",
            text = """
                Whenever Severus enters or attacks, you may sacrifice another creature. If you do, draw a card.
                Whenever another creature dies, target opponent loses 1 life and you gain 1 life. If this is the fourth time this ability has resolved this turn, transform Severus.
            """,
        ) to abilities(
            triggered(entersOrAttacksSelf, Step.Pay(cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1))),
            triggered(dies(and(another, creature)), loseLife(1, Players.Target("opponent")), gainLife(1),
                ifThen(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(4)), transformSelf), targets = listOf(targetOpponent)),
        ),
        card(
            name = "Severus, One-Winged Seraph",
            cost = null,
            type = "Legendary Creature — Angel Nightmare Avatar",
            colorIndicator = "B",
            pt = "5/5",
            text = """
                Flying
                Super Nova — As this creature transforms into Severus, One-Winged Seraph, you get an emblem with "Whenever a creature dies, target opponent loses 1 life and you gain 1 life."
                Whenever Severus attacks, you may sacrifice any number of other creatures. If you do, draw that many cards.
            """,
        ) to abilities(
            Keyword.Flying,
            asTransformsInto("Severus, One-Winged Seraph", doing(Effect.CreateEmblem(listOf(
                triggered(dies(creature), loseLife(1, Players.Target("opponent")), gainLife(1), targets = listOf(targetOpponent))), Players.You)))
                .copy(abilityWord = "Super Nova"),
            triggered(attacksSelf,
                Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), null), postBind = "sacrificed")), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("sacrificed")))))),
        ),
    )

    /** "the chosen creature" (remembered by the card, CR 607.2a); "When the chosen player loses the game, you win the game." */
    @Test
    fun zerandTheUnchained() = cardFaces(Layout.Transform,
        card(
            name = "Zerand the Unchained",
            cost = "{3}{B}{B}",
            type = "Legendary Creature — Human Noble Warrior",
            pt = "4/4",
            text = """
                My First Friend — When Zerand the Unchained enters, choose a creature an opponent controls. Until end of turn, creatures other than Zerand the Unchained and the chosen creature get -2/-2.
                When the chosen creature leaves the battlefield, transform Zerand the Unchained.
            """,
        ) to abilities(
            triggered(entersSelf,
                Step.Choose(Choice.Objects(creatureOpponentControls, n(1), n(1)), bind = "chosen", chooser = Players.You, remember = true),
                pump(Objects.Each(and(creature, not(ObjectFilter.Is(Objects.Self)), not(ObjectFilter.Is(Objects.Remembered("chosen"))))), -2, -2),
                abilityWord = "My First Friend"),
            triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Remembered("chosen")), from = Zone.Battlefield)), transformSelf),
        ),
        card(
            name = "Shenra, Transcendent Rival",
            cost = null,
            type = "Legendary Creature — Dragon",
            colorIndicator = "B",
            pt = "8/8",
            text = """
                Flying
                As this creature transforms into Shenra, choose an opponent.
                Burning Chains — When the chosen player loses the game, you win the game.
            """,
        ) to abilities(
            Keyword.Flying,
            asTransformsInto("Shenra, Transcendent Rival", Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "chosen player", chooser = Players.You, remember = true)),
            triggered(on(EventPattern.LoseGame(PlayerFilter.Is(Players.Remembered("chosen player")))), doing(Effect.WinGame(Players.You)), abilityWord = "Burning Chains"),
        ),
    )

    @Test
    fun valenShadowGunner() = cardFaces(Layout.Transform,
        card(
            name = "Valen Shadegunner",
            cost = "{2}{B}{B}",
            type = "Legendary Creature — Assassin",
            pt = "2/2",
            text = """
                Whenever a creature an opponent controls dies, put a number of +1/+1 counters on Valen Shadegunner equal to that creature's power.
                Whenever Valen Shadegunner attacks, you may transform it.
            """,
        ) to abilities(
            triggered(dies(creatureOpponentControls), counters(Objects.Self, Value.Of(NumericProperty.Power, leftOne, Combine.Single))),
            triggered(attacksSelf, Step.May(listOf(transformSelf), Players.You)),
        ),
        card(
            name = "Wildborn Beast",
            cost = null,
            type = "Legendary Creature — Werewolf Beast",
            colorIndicator = "B",
            pt = "3/2",
            text = """
                Trample, lifelink
                When Wildborn Beast dies, return it to the battlefield tapped (front face up).
            """,
        ) to abilities(
            Keyword.Trample,
            Keyword.Lifelink,
            triggered(diesSelf, move(enteredOne, Zone.Battlefield, tapped = true)),
        ),
    )

    // CR 712.20 is not used here: "you may transform" at upkeep; "If a card or token would be put into your graveyard from anywhere, exile it instead."
    @Test
    fun aethelUnsundered() = cardFaces(Layout.Transform,
        card(
            name = "Aethel, Unsundered",
            cost = "{1}{U}{B}",
            type = "Legendary Creature — Elder Wizard",
            pt = "2/4",
            text = """
                Vigilance
                Whenever Aethel enters or attacks, draw a card, then discard a card.
                At the beginning of your upkeep, if there are fourteen or more cards in your graveyard, you may transform Aethel.
            """,
        ) to abilities(
            Keyword.Vigilance,
            triggered(entersOrAttacksSelf, draw(1), discard(1)),
            triggered(yourUpkeep, Step.May(listOf(transformSelf), Players.You), interveningIf = Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 14)),
        ),
        card(
            name = "Aethel, Sorcerer of the Deep",
            cost = null,
            type = "Legendary Creature — Avatar",
            colorIndicator = "BU",
            pt = "6/6",
            text = """
                Vigilance
                Echo of the Lost — During your turn, you may play cards from your graveyard.
                If a card or token would be put into your graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Vigilance,
            static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You), affects = Objects.Each(inYourGraveyard(cardFilter)),
                condition = Condition.YourTurn).copy(abilityWord = "Echo of the Lost"),
            static(Mechanic.Replacement(EventPattern.ZoneChange(and(or(cardFilter, token), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        ),
    )

    @Test
    fun theTyrantOfVesh() = cardFaces(Layout.Transform,
        card(
            name = "The Tyrant of Vesh",
            cost = "{U}{R}",
            type = "Legendary Creature — Human Noble Wizard",
            pt = "2/2",
            text = """
                {T}: Add {U} or {R}. Spend this mana only to cast a noncreature spell.
                Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on The Tyrant of Vesh. Then if it has three or more +1/+1 counters on it, transform it.
            """,
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(arcanum.api.Color.Blue, arcanum.api.Color.Red)
                .map { arcanum.api.ColorRef.Literal(it) }), restriction = and(spellFilter, not(creature))), Players.You))),
            triggered(youCast(noncreature), counters(Objects.Self), ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 3)), transformSelf),
                interveningIf = manaSpentAtLeast(4)),
        ),
        card(
            name = "The Lord of the Abyss",
            cost = null,
            type = "Legendary Creature — Demon Noble Wizard",
            colorIndicator = "RU",
            pt = "3/3",
            text = """
                Starfall — Whenever The Lord of the Abyss attacks, it deals X damage to each opponent, where X is the number of noncreature, nonland cards in your graveyard.
            """,
        ) to abilities(
            triggered(attacksSelf, damage(count(inYourGraveyard(noncreature, nonland)), eachOpponent), abilityWord = "Starfall"),
        ),
    )

    /** "a card for each card type among cards discarded this way" (CR 205.2a). */
    @Test
    fun kaelCourtJester() = cardFaces(Layout.Transform,
        card(
            name = "Kael, Court Jester",
            cost = "{2}{U}{B}{R}",
            type = "Legendary Creature — Human Wizard",
            pt = "4/5",
            text = """
                Whenever Kael enters or attacks, each player discards a card. Then you draw a card for each card type among cards discarded this way.
                {8}: Each opponent sacrifices a permanent of their choice. Transform Kael. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersOrAttacksSelf, doing(Effect.Discard(Players.Each(PlayerFilter.Any), n(1), ObjectFilter.Any, postBind = "discarded")),
                doing(Effect.Draw(Players.You, Value.CountDistinct(Objects.Bound("discarded"), SharedQuality.CardType)))),
            activated(manaCost("{8}"), Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", steps = listOf(
                Step.Choose(Choice.Objects(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ), together = true), transformSelf, rules = asSorcery),
        ),
        card(
            name = "Kael, Ruler of Ruin",
            cost = null,
            type = "Legendary Creature — Avatar Wizard",
            colorIndicator = "BRU",
            pt = "5/7",
            text = """
                Flying
                Whenever an opponent loses life during your turn, you draw that many cards.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(on(EventPattern.LoseLife(PlayerFilter.Opponent)), Instructions(steps = listOf(doing(Effect.Draw(Players.You, Value.EventAmount)))),
                triggersOnlyIf = Condition.YourTurn),
        ),
    )

    // CR 614.1a: damage doubled
    @Test
    fun ravelGenomeSorcerer() = cardFaces(Layout.Transform,
        card(
            name = "Ravel, Genome Sorcerer",
            cost = "{2}{B}{R}",
            type = "Legendary Creature — Human Mutant Wizard",
            pt = "3/4",
            text = """
                At the beginning of your end step, create a tapped 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent." Then if you control four or more Wizards, transform Ravel.
            """,
        ) to abilities(
            triggered(yourEndStep, create(wizardToken, tapped = true), ifThen(Condition.Exists(and(sub("Wizard"), you), atLeast = 4), transformSelf)),
        ),
        card(
            name = "Trance Ravel, Fate Defied",
            cost = null,
            type = "Legendary Creature — Avatar Wizard",
            colorIndicator = "BR",
            pt = "4/6",
            text = """
                Flare Star — If a Wizard you control would deal damage to a permanent or player, it deals double that damage instead.
            """,
        ) to abilities(
            static(Mechanic.Replacement(EventPattern.Damage(and(sub("Wizard"), you), RecipientFilter(objects = permanentFilter, players = PlayerFilter.Any)),
                modify = listOf(Modification.MultiplyAmount(2)))).copy(abilityWord = "Flare Star"),
        ),
    )

    @Test
    fun ysoldeTimeSorceress() = cardFaces(Layout.Transform,
        card(
            name = "Ysolde, Time Sorceress",
            cost = "{3}{U}{B}",
            type = "Legendary Creature — Human Warlock",
            pt = "4/5",
            text = """
                Whenever Ysolde enters or attacks, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
                At the beginning of your end step, you may pay {4}{U}{U}{B}{B} and exile eight cards from your graveyard. If you do, transform Ysolde.
            """,
        ) to abilities(
            triggered(entersOrAttacksSelf, surveil(2)),
            triggered(yourEndStep,
                Step.Pay(cost(mana("{4}{U}{U}{B}{B}"), CostPart.Exile(CostObjects.Choose(cardFilter, n(8)), Zone.Graveyard)), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), transformSelf)),
        ),
        card(
            name = "Ysolde, Omnipotent",
            cost = null,
            type = "Legendary Creature — Nightmare Warlock",
            colorIndicator = "BU",
            pt = "7/7",
            text = """
                Menace (This creature can't be blocked except by two or more creatures.)
                Time Compression — When this creature transforms into Ysolde, Omnipotent, take an extra turn after this one.
            """,
        ) to abilities(
            Keyword.Menace,
            triggered(on(transformsInto("Ysolde, Omnipotent")), doing(Effect.ExtraTurn(Players.You)), abilityWord = "Time Compression"),
        ),
    )

    @Test
    fun morvathVoidWarlock() = cardFaces(Layout.Transform,
        card(
            name = "Morvath, Void Warlock",
            cost = "{1}{B}{G}",
            type = "Legendary Creature — Spirit Warlock",
            pt = "3/3",
            text = """
                When Morvath enters, you gain 3 life.
                At the beginning of your end step, if there are six or more permanent cards in your graveyard, transform Morvath.
            """,
        ) to abilities(
            triggered(entersSelf, gainLife(3)),
            triggered(yourEndStep, transformSelf, interveningIf = Condition.Exists(inYourGraveyard(permanentCard), atLeast = 6)),
        ),
        card(
            name = "Neo Morvath, Dimension's End",
            cost = null,
            type = "Legendary Creature — Spirit Avatar",
            colorIndicator = "BG",
            pt = "*/3",
            text = """
                Trample
                Neo Morvath's power is equal to the number of permanent cards in your graveyard.
            """,
        ) to abilities(
            Keyword.Trample,
            static(Mechanic.SetPowerToughness(count(inYourGraveyard(permanentCard)), null), characteristicDefining = true),
        ),
    )

    @Test
    fun sylvaFarron() = cardFaces(Layout.Transform,
        card(
            name = "Sylva Farron",
            cost = "{1}{G}{W}",
            type = "Legendary Creature — Human Citizen",
            pt = "2/2",
            text = """
                The first legendary creature spell you cast each turn costs {2} less to cast.
                At the beginning of combat on your turn, if you control two or more other legendary creatures, you may transform Sylva Farron.
            """,
        ) to abilities(
            firstLegendaryCheaper,
            triggered(beginningOfCombatOnYourTurn, Step.May(listOf(transformSelf), Players.You),
                interveningIf = Condition.Exists(and(another, legendary, creature, you), atLeast = 2)),
        ),
        card(
            name = "Crystallized Sylva",
            cost = null,
            type = "Legendary Artifact",
            colorIndicator = "GW",
            text = """
                The first legendary creature spell you cast each turn costs {2} less to cast.
                Legendary creatures you control get +2/+2.
            """,
        ) to abilities(
            firstLegendaryCheaper,
            static(pt(2, 2), affects = Objects.Each(and(legendary, creature, you))),
        ),
    )

    @Test
    fun skyhavenAcademy() = cardFaces(Layout.Transform,
        card(
            name = "Skyhaven, Mercenary Academy",
            cost = null,
            type = "Land — Town",
            text = """
                This land enters tapped.
                {T}: Add {G} or {U}.
                {5}{G}{U}, {T}: Transform this land. This ability costs {1} less to activate for each other Town you control.
            """,
        ) to abilities(
            entersTapped,
            addEither("G", "U"),
            activated(cost(mana("{5}{G}{U}"), tap), transformSelf, rules = anyTime.copy(costChange = arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!,
                count(and(another, town, you))))),
        ),
        card(
            name = "Skyhaven, Airborne",
            cost = null,
            type = "Legendary Artifact — Vehicle",
            pt = "5/4",
            text = """
                Flying
                Whenever Skyhaven attacks, draw a card.
                Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
            """,
        ) to abilities(Keyword.Flying, triggered(attacksSelf, draw(1)), Keyword.Crew(1)),
    )

    /** "The first legendary creature spell you cast each turn costs {2} less to cast." (the spell being cast counts, CR 601.2f) */
    private val firstLegendaryCheaper = static(Mechanic.CostChange(and(legendary, creature, spellFilter, you), arcanum.api.CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
        condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(and(legendary, creature), PlayerFilter.You), atLeast = 1, measure = Measure.Events)))
}
