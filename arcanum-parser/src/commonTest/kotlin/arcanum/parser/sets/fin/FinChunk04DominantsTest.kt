package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/**
 * Final Fantasy chunk 04: double-faced cards whose back face is a Saga creature (CR 712, 714): exiled and returned transformed by an
 * activated ability (CR 701.27), their last chapter exiling and returning them front face up (CR 712.14); a spell cast from a graveyard that
 * becomes its own back face with a finality counter (CR 122.1h); a creature returned transformed from the graveyard; a dies trigger that puts
 * the card on the bottom of its owner's library.
 */
class FinChunk04DominantsTest {
    /** "{cost}, {T}: Exile [this], then return it to the battlefield transformed under its owner's control. Activate only as a sorcery." */
    private fun transformAbility(costText: String, tapCost: Boolean = true) = activated(
        if (tapCost) cost(mana(costText), tap) else cost(mana(costText)),
        move(Objects.Self, Zone.Exile, postBind = "exiled"),
        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
        rules = asSorcery)

    /** "Exile [this], then return it to the battlefield (front face up)." */
    private val returnFrontFaceUp = exileAndReturnFrontFaceUp().toTypedArray()

    // CR 712.14 (front face up), 615 (prevention), 701.27
    @Test
    fun shardOfTheJudge() = cardFaces(Layout.Transform,
        card(
            name = "Shard of the Arbiter",
            cost = "{W}",
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +1/+1.
                {5}{W}{W}: Exile this Equipment, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
                Equip {1}
            """,
        ) to abilities(
            static(pt(1, 1), affects = equipped),
            transformAbility("{5}{W}{W}", tapCost = false),
            Keyword.Equip(manaCost("{1}")),
        ),
        card(
            name = "Conjure: The Arbiter",
            cost = null,
            type = "Enchantment Creature — Saga Construct",
            colorIndicator = "W",
            pt = "4/3",
            text = """
                (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
                I, II — Prevent all damage that would be dealt to creatures you control this turn.
                III — Tap all creatures your opponents control.
                Flying
            """,
        ) to abilities(
            chapters(listOf(1, 2), doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(arcanum.api.ObjectFilter.Any,
                arcanum.api.RecipientFilter(objects = arcanum.api.ObjectFilter.Is(Objects.Each(creatureYouControl)))))), null, arcanum.api.Duration.EndOfTurn))),
            chapter(3, tapIt(Objects.Each(creatureOpponentControls))),
            Keyword.Flying,
        ),
    )

    @Test
    fun aurelKnightOfWings() = cardFaces(Layout.Transform,
        card(
            name = "Aurel, Wyrm-Sworn Knight",
            cost = "{3}{W}",
            type = "Legendary Creature — Human Noble Knight",
            pt = "3/3",
            text = """
                Dragonfire Dive — During your turn, Aurel and other Knights you control have flying.
                When Aurel enters, create a 2/2 white Knight creature token.
                {4}{W}{W}, {T}: Exile Aurel, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            static(has(Keyword.Flying), affects = Objects.Union(listOf(Objects.Self, Objects.Each(and(another, sub("Knight"), you)))), condition = Condition.YourTurn)
                .copy(abilityWord = "Dragonfire Dive"),
            triggered(entersSelf, create(knightToken())),
            transformAbility("{4}{W}{W}"),
        ),
        card(
            name = "Radiant Wyrm, Warden of Dawn",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Dragon",
            colorIndicator = "W",
            pt = "5/5",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II — Wings of Light — Put a +1/+1 counter on each other creature you control. Those creatures gain flying until end of turn.
                III — Gigaflare — Destroy target permanent. Exile Radiant Wyrm, then return it to the battlefield (front face up).
                Flying
            """,
        ) to abilities(
            chapters(listOf(1, 2), counters(Objects.Each(and(another, creature, you))), gains(Objects.Each(and(another, creature, you)), Keyword.Flying)),
            chapter(3, destroy(target("permanent")), *returnFrontFaceUp, targets = listOf(slot("permanent", permanentFilter))),
            Keyword.Flying,
        ),
    )

    @Test
    fun maraFrostbornNoble() = cardFaces(Layout.Transform,
        card(
            name = "Mara, Frostbound Noble",
            cost = "{2}{U}",
            type = "Legendary Creature — Human Noble Warrior",
            pt = "2/2",
            text = """
                When Mara enters, return up to one other target nonland permanent to its owner's hand.
                {3}{U}{U}, {T}: Exile Mara, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonlandPermanent), min = 0))),
            transformAbility("{3}{U}{U}"),
        ),
        card(
            name = "Rimeheart, Warden of Frost",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Elemental",
            colorIndicator = "U",
            pt = "4/5",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II — Mesmerize — Target creature can't be blocked this turn.
                III — Cold Snap — Tap all lands your opponents control. Exile Rimeheart, then return it to the battlefield (front face up).
            """,
        ) to abilities(
            chapters(listOf(1, 2), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
            chapter(3, tapIt(Objects.Each(and(land, opponents))), *returnFrontFaceUp),
        ),
    )

    // CR 700.5 (devotion)
    @Test
    fun corvanEmberDominant() = cardFaces(Layout.Transform,
        card(
            name = "Corvan, Emberbound Noble",
            cost = "{4}{R}{R}",
            type = "Legendary Creature — Human Noble Warrior",
            pt = "5/5",
            text = """
                When Corvan enters, you may discard your hand, then draw cards equal to your devotion to red. (Each {R} in the mana costs of permanents you control counts toward your devotion to red.)
                {4}{R}{R}, {T}: Exile Corvan, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersSelf, Step.May(listOf(
                doing(Effect.Discard(Players.You, n(1), arcanum.api.ObjectFilter.Any,
                    objects = Objects.Each(and(inZone(Zone.Hand), arcanum.api.ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))))),
                doing(Effect.Draw(Players.You, Value.Devotion(listOf(arcanum.api.Color.Red), Players.You))),
            ), Players.You)),
            transformAbility("{4}{R}{R}"),
        ),
        card(
            name = "Pyreheart, Warden of Cinders",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Demon",
            colorIndicator = "R",
            pt = "9/9",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Lunge — Pyreheart fights up to one other target creature.
                II, III — Brimstone — Add {R}{R}{R}{R}. If Pyreheart has three or more lore counters on it, exile it, then return it to the battlefield (front face up).
            """,
        ) to abilities(
            chapter(1, doing(Effect.Fight(Objects.Self, target("creature"))), targets = listOf(slot("creature", and(another, creature), min = 0))),
            chapters(listOf(2, 3), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}{R}{R}")!!)), Players.You)),
                ifThen(Condition.ObjectMatches(Objects.Self, arcanum.api.ObjectFilter.HasCounter(lore, atLeast = 3)),
                    *returnFrontFaceUp)),
        ),
    )

    // CR 115.1 ("with total mana value 6 or less")
    @Test
    fun cyrenPhoenixDominant() = cardFaces(Layout.Transform,
        card(
            name = "Cyren, Ashborn Noble",
            cost = "{1}{R}{W}",
            type = "Legendary Creature — Human Noble Wizard",
            pt = "3/4",
            text = """
                When Cyren enters, discard up to two cards, then draw that many cards.
                {3}{R}{W}, {T}: Exile Cyren, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersSelf,
                Step.Choose(arcanum.api.Choice.Objects(and(inZone(Zone.Hand), arcanum.api.ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), n(0), n(2)),
                    bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.You, n(2), arcanum.api.ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("chosen"))))),
            transformAbility("{3}{R}{W}"),
        ),
        card(
            name = "Ashwing, Warden of Embers",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Phoenix",
            colorIndicator = "RW",
            pt = "4/4",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II — Rising Flames — Ashwing deals 2 damage to each opponent.
                III — Flames of Rebirth — Return any number of target creature cards with total mana value 6 or less from your graveyard to the battlefield. Exile Ashwing, then return it to the battlefield (front face up).
                Flying, lifelink
            """,
        ) to abilities(
            chapters(listOf(1, 2), damage(2, eachOpponent)),
            chapter(3, move(target("cards"), Zone.Battlefield), *returnFrontFaceUp, targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = null)
                .copy(totalAtMost = arcanum.api.Choice.TotalLimit(NumericProperty.ManaValue, n(6))))),
            Keyword.Flying,
            Keyword.Lifelink,
        ),
    )

    /** "Put up to one enchantment card milled this way into your hand"; the Saga copy "If it's a Saga, put up to three lore counters on it". */
    @Test
    fun taliaMagitekAdept() = cardFaces(Layout.Transform,
        card(
            name = "Talia, Arcane Prodigy",
            cost = "{1}{R}{G}",
            type = "Legendary Creature — Human Wizard Warrior",
            pt = "4/2",
            text = """
                When Talia enters, mill five cards. Put up to one enchantment card milled this way into your hand.
                Trance — {4}{R}{G}, {T}: Exile Talia, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersSelf, mill(5, postBind = "milled"),
                Step.Choose(arcanum.api.Choice.Objects(and(enchantment, inZone(Zone.Graveyard)), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen",
                    chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
            transformAbility("{4}{R}{G}").copy(abilityWord = "Trance"),
        ),
        card(
            name = "Arcane Talia",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Wizard",
            colorIndicator = "GR",
            pt = "6/6",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II, III — Create a token that's a copy of target nonlegendary enchantment you control. It gains haste. If it's a Saga, put up to three lore counters on it. Sacrifice it at the beginning of your next end step.
                IV — Add {W}{W}, {U}{U}, {B}{B}, {R}{R}, and {G}{G}. Exile Arcane Talia, then return it to the battlefield (front face up).
                Flying
            """,
        ) to abilities(
            chapters(listOf(1, 2, 3),
                doing(Effect.CreateToken(TokenSpec.CopyOf(target("enchantment")), n(1), Players.You, bind = "tokens")),
                doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), arcanum.api.Affected.Objects(Objects.Bound("tokens")), arcanum.api.Duration.Indefinitely)),
                ifThen(Condition.ObjectMatches(Objects.Bound("tokens"), saga),
                    Step.Choose(arcanum.api.Choice.Number(n(0), n(3)), bind = "lore", chooser = Players.You),
                    doing(Effect.PutCounters(Objects.Bound("tokens"), lore, Value.Bound("lore")))),
                delayed(stepBegins(TurnStep.End, arcanum.api.PlayerFilter.You), doing(Effect.Sacrifice(Objects.Bound("tokens")))),
                targets = listOf(slot("enchantment", and(not(legendary), enchantment, you)))),
            chapter(4, doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}{W}{U}{U}{B}{B}{R}{R}{G}{G}")!!)), Players.You)), *returnFrontFaceUp),
            Keyword.Flying,
        ),
    )

    /** "If this spell was cast from a graveyard, exile it, then put it onto the battlefield transformed … with a finality counter on it." */
    // CR 702.34a (flashback), 712.14, 122.1h
    @Test
    fun espersAwakening() = cardFaces(Layout.Transform,
        card(
            name = "Esper's Awakening",
            cost = "{1}{G}",
            type = "Sorcery",
            text = """
                Surveil 2. You gain 2 life. If this spell was cast from a graveyard, exile it, then put it onto the battlefield transformed under its owner's control with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
                Flashback {3}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
            """,
        ) to abilities(
            spell(surveil(2), gainLife(2), ifThen(Condition.CastFrom(Zone.Graveyard, Objects.Self),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true,
                    counters = listOf(CountersToPut(finality, n(1)))))))),
            Keyword.Flashback(manaCost("{3}{G}")),
        ),
        card(
            name = "Conjure: Grove Esper",
            cost = null,
            type = "Enchantment Creature — Saga Elemental",
            colorIndicator = "G",
            pt = "4/4",
            text = """
                (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
                I — Reveal the top card of your library. If it's a permanent card, put it into your hand.
                II — Add {G}{G}.
                III — Other creatures you control get +2/+2 and gain trample until end of turn.
            """,
        ) to abilities(
            chapter(1, doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
                ifThen(Condition.ObjectMatches(Objects.Bound("revealed"), permanentCard), move(Objects.Bound("revealed"), Zone.Hand))),
            chapter(2, doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!)), Players.You))),
            chapter(3, pump(Objects.Each(and(another, creature, you)), 2, 2, has(Keyword.Trample))),
        ),
    )

    @Test
    fun brannoReluctantGuardian() = cardFaces(Layout.Transform,
        card(
            name = "Branno, Reluctant Guardian",
            cost = "{3}{B}",
            type = "Legendary Creature — Human Warrior",
            pt = "4/3",
            text = """
                Menace
                Whenever Branno deals combat damage to a player, you may exile it, then return it to the battlefield transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Menace,
            triggered(combatDamageToAPlayer, Step.May(listOf(
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
            ), Players.You)),
        ),
        card(
            name = "Branno's Final Aeon",
            cost = null,
            type = "Legendary Enchantment Creature — Saga Nightmare",
            colorIndicator = "B",
            pt = "7/7",
            text = """
                (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
                I, II — Jecht Beam — Each opponent discards a card and you draw a card.
                III — Ultimate Jecht Shot — Each opponent sacrifices two creatures of their choice.
                Menace
            """,
        ) to abilities(
            chapters(listOf(1, 2), discard(1, Players.Each(arcanum.api.PlayerFilter.Opponent)), draw(1)),
            chapter(3, Step.ForEach(arcanum.api.Each.Players(Players.Each(arcanum.api.PlayerFilter.Opponent)), "opponent", steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(and(creature, arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(Players.Bound("opponent")))),
                    n(2), n(2)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ), together = true)),
            Keyword.Menace,
        ),
    )

    /** An activated ability that works from the graveyard (CR 113.6m: "from your graveyard"); a dies trigger of the back face. */
    @Test
    fun gerrandFallenKnight() = cardFaces(Layout.Transform,
        card(
            name = "Gerrand, Fallen Knight",
            cost = "{B}{R}",
            type = "Legendary Creature — Human Knight",
            pt = "3/2",
            text = """
                Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
                {3}{B}{B}{R}{R}: Return this card from your graveyard to the battlefield transformed. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(youCast(noncreature), surveil(1)),
            activated(manaCost("{3}{B}{B}{R}{R}"), Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, transformed = true))), rules = asSorcery),
        ),
        card(
            name = "Ruin, the Endless",
            cost = null,
            type = "Legendary Creature — Demon",
            colorIndicator = "BR",
            pt = "5/5",
            text = """
                Flying
                When Ruin dies, put it on the bottom of its owner's library.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(diesSelf, move(enteredOne, Zone.Library, position = -1)),
        ),
    )

    // CR 603.4 ("This ability triggers only once each turn"), 701.27
    @Test
    fun aelthaHeartOfTheCrystal() = cardFaces(Layout.Transform,
        card(
            name = "Aeltha, Heart of the Crystal",
            cost = "{1}{W}{W}",
            type = "Legendary Creature — Elder Wizard",
            pt = "3/3",
            text = """
                Whenever you cast a legendary spell, draw a card. This ability triggers only once each turn.
                Hero's Sundering — {7}, {T}: Exile target nonland permanent. Transform Aeltha. Activate only as a sorcery.
            """,
        ) to abilities(
            Ability.Triggered(youCast(legendary), arcanum.api.Instructions(steps = listOf(draw(1))), perTurn = 1),
            activated(cost(mana("{7}"), tap), move(target("permanent"), Zone.Exile), doing(Effect.Transform(Objects.Self)),
                targets = listOf(slot("permanent", nonlandPermanent)), rules = asSorcery).copy(abilityWord = "Hero's Sundering"),
        ),
        card(
            name = "Aeltha, the Mothercrystal",
            cost = null,
            type = "Legendary Creature — God",
            colorIndicator = "W",
            pt = "4/4",
            text = """
                Indestructible
                Blessing of Light — At the beginning of combat on your turn, put a +1/+1 counter on another target creature you control. Until your next turn, it gains indestructible. If that creature is legendary, draw a card.
            """,
        ) to abilities(
            Keyword.Indestructible,
            triggered(beginningOfCombatOnYourTurn, counters(target("creature")),
                doing(Effect.ApplyMechanics(listOf(has(Keyword.Indestructible)), arcanum.api.Affected.Objects(target("creature")), arcanum.api.Duration.UntilYourNextTurn)),
                ifThen(Condition.ObjectMatches(target("creature"), legendary), draw(1)),
                targets = listOf(slot("creature", and(another, creature, you))), abilityWord = "Blessing of Light"),
        ),
    )
}
