package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.Comparison
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.parser.OracleCard
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaCost
import arcanum.api.Instructions
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/*
 * INR chunk 02: transforming double-faced cards, part 2 (CR 701.27, 712). The transform comes from an activated ability, a trigger's
 * "then if" step, a delayed trigger, or a cost; a front face's "Activate only if" is a condition of the activation (CR 602.5b).
 */
class InrChunk02TransformTest {
    // CR 712.2 (a land's back face is a creature), 701.27a, 502.3 ("then untap it": the same permanent, which stays transformed)
    @Test
    fun nightfallChapel() = cardFaces(Layout.Transform,
        card(
            name = "Nightfall Chapel",
            cost = null,
            type = "Land",
            text = """
                {T}: Add {C}.
                {5}, {T}, Pay 1 life: Create a 1/1 white and black Human Cleric creature token.
                {5}, {T}, Sacrifice five creatures: Transform this land, then untap it.
            """,
        ) to abilities(
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
            activated(cost(mana("{5}"), tap, CostPart.PayLife(n(1))), create(creatureToken("Human Cleric", listOf(Color.White, Color.Black), 1, 1))),
            activated(cost(mana("{5}"), tap, CostPart.Sacrifice(CostObjects.Choose(creature, n(5)))), transformSelf, untapIt(Objects.Self)),
        ),
        card(
            name = "Lord of Nightfall",
            cost = null,
            type = "Legendary Creature — Demon",
            text = "Flying, lifelink, indestructible, haste",
            pt = "9/7",
            colorIndicator = "B",
        ) to abilities(Keyword.Flying, Keyword.Lifelink, Keyword.Indestructible, Keyword.Haste),
    )

    // CR 603.7 (delayed trigger), 701.27a; the back face's trigger watches its own transformation (CR 701.27f)
    @Test
    fun seraphOfVigil() = cardFaces(Layout.Transform,
        card(
            name = "Seraph of Vigil",
            cost = "{3}{W}{W}",
            type = "Legendary Creature — Angel",
            text = """
                Flash
                Flying, vigilance
                When Seraph of Vigil enters, creatures you control gain indestructible until end of turn.
                When a non-Angel creature you control dies, transform Seraph of Vigil at the beginning of the next upkeep.
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Flash, Keyword.Flying, Keyword.Vigilance,
            triggered(entersSelf, gains(Objects.Each(and(creature, you)), Keyword.Indestructible)),
            triggered(dies(and(not(sub("Angel")), creature, you)), delayed(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), transformSelf)),
        ),
        card(
            name = "Vigil Cleanser",
            cost = null,
            type = "Legendary Creature — Angel",
            text = """
                Flying
                When this creature transforms into Vigil Cleanser, it deals 3 damage to each other creature and each opponent.
            """,
            pt = "6/5",
            colorIndicator = "W",
        ) to abilities(
            Keyword.Flying,
            triggered(Trigger.On(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Vigil Cleanser")))),
                damage(3, Recipients(objects = Objects.Each(and(another, creature)), players = Players.Each(PlayerFilter.Opponent)))),
        ),
    )

    // CR 602.5b: "Activate only if you control five or more Vampires" is a condition on the activation, checked as it is activated
    @Test
    fun crypticMatriarch() = cardFaces(Layout.Transform,
        card(
            name = "Cryptic Matriarch",
            cost = "{2}{B}{B}",
            type = "Creature — Vampire",
            text = """
                Flying
                {T}: Create a 2/2 black Vampire creature token with flying.
                {B}: Transform this creature. Activate only if you control five or more Vampires.
            """,
            pt = "3/3",
        ) to abilities(
            Keyword.Flying,
            activated(cost(tap), create(vampire2)),
            activated(manaCost("{B}"), transformSelf, rules = ActivationRules(Timing.Any, PlayerFilter.You, condition = youControl(sub("Vampire"), 5))),
        ),
        card(
            name = "Matriarch of Night",
            cost = null,
            type = "Creature — Vampire",
            text = """
                Flying
                Other Vampire creatures you control get +2/+2.
                {T}: Create a 2/2 black Vampire creature token with flying.
            """,
            pt = "5/5",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            static(pt(2, 2), affects = Objects.Each(and(another, sub("Vampire"), creature, you))),
            activated(cost(tap), create(vampire2)),
        ),
    )

    // CR 119.2 (life total), 103.4 (starting life total); the "then if" is checked on resolution after the life is gained
    @Test
    fun grailOfPlenty() = cardFaces(Layout.Transform,
        card(
            name = "Grail of Plenty",
            cost = "{3}",
            type = "Artifact",
            text = "{T}: You gain 1 life. Then if you have at least 10 life more than your starting life total, transform this artifact.",
        ) to abilities(
            activated(cost(tap), gainLife(1), ifThen(Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, Value.Sum(listOf(Value.StartingLife(Players.You), n(10)))), transformSelf)),
        ),
        card(
            name = "Grail of Ruin",
            cost = null,
            type = "Artifact",
            text = "{T}: Target player loses 5 life.",
            colorIndicator = null,
        ) to abilities(
            activated(cost(tap), loseLife(5, Players.Target("player")), targets = listOf(targetPlayer)),
        ),
    )

    // CR 603.10a (leaves-the-battlefield trigger looks back), 701.27a
    @Test
    fun hungryFarmhand() = cardFaces(Layout.Transform,
        card(
            name = "Hungry Farmhand",
            cost = "{2}{B}",
            type = "Creature — Human Peasant",
            text = """
                Lifelink
                When another creature you control dies, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Lifelink,
            triggered(dies(and(another, creature, you)), transformSelf),
        ),
        card(
            name = "Grim Reaper of Fields",
            cost = null,
            type = "Creature — Human Knight",
            text = "Lifelink",
            pt = "4/3",
            colorIndicator = "B",
        ) to abilities(Keyword.Lifelink),
    )

    // CR 701.27a; a token made first counts toward "three or more Wizards"
    @Test
    fun scholarOfPerfection() = cardFaces(Layout.Transform,
        card(
            name = "Scholar of Perfection",
            cost = "{3}{U}{U}",
            type = "Creature — Insect Horror",
            text = """
                Flying
                Whenever you cast an instant or sorcery spell, create a 1/1 blue Human Wizard creature token. Then if you control three or more Wizards, transform this creature.
            """,
            pt = "5/4",
        ) to abilities(
            Keyword.Flying,
            triggered(youCast(or(instant, sorcery)), create(wizardToken), ifThen(youControl(sub("Wizard"), 3), transformSelf)),
        ),
        card(
            name = "Final Revision",
            cost = null,
            type = "Creature — Eldrazi Insect",
            text = """
                Flying
                Wizards you control get +2/+1 and have flying.
                Whenever you cast an instant or sorcery spell, create a 1/1 blue Human Wizard creature token.
            """,
            pt = "6/5",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
            static(pt(2, 1), has(Keyword.Flying), affects = Objects.Each(and(sub("Wizard"), you))),
            triggered(youCast(or(instant, sorcery)), create(wizardToken)),
        ),
    )

    // CR 701.27a, 602.5d ("once each turn")
    @Test
    fun eagerAcolyte() = cardFaces(Layout.Transform,
        card(
            name = "Eager Acolyte",
            cost = "{B}",
            type = "Creature — Human Wizard",
            text = "{2}{B}, Sacrifice another creature: Draw a card, then transform this creature. Activate only once each turn.",
            pt = "1/1",
        ) to abilities(
            activated(cost(mana("{2}{B}"), sacrificeA(and(another, creature))), draw(1), transformSelf,
                rules = ActivationRules(Timing.Any, PlayerFilter.You, perTurn = 1)),
        ),
        card(
            name = "Risen Fiend",
            cost = null,
            type = "Creature — Demon",
            text = "",
            pt = "4/4",
            colorIndicator = "B",
        ) to abilities(),
    )

    // CR 701.27a, 310.11b (a Siege is cast transformed when defeated), 712.8 (the back face is an enchantment)
    @Test
    fun invasionOfTheMoors() = cardFaces(Layout.Transform,
        OracleCard(
            "Invasion of the Moors", "{2}{B}{B}", "Battle — Siege", """
                (As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)
                Flash
                When this Siege enters, target creature an opponent controls gets -13/-13 until end of turn.
            """.trimIndent(), defense = "5",
        ) to abilities(
            Keyword.Flash,
            triggered(entersSelf, untilEndOfTurn(target("creature"), pt(-13, -13)), targets = listOf(slot("creature", and(creature, opponents)))),
        ),
        card(
            name = "Rising of the Dead",
            cost = null,
            type = "Enchantment",
            text = """
                When this enchantment enters, create two 2/2 black Zombie creature tokens.
                {2}{B}: Exile target card from a graveyard. If it was a creature card, create a 2/2 black Zombie creature token.
            """,
            colorIndicator = "B",
        ) to abilities(
            triggered(entersSelf, create(zombie, count = n(2))),
            activated(manaCost("{2}{B}"), Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
                ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature), create(zombie)),
                targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        ),
    )

    // CR 701.27a, 701.27c; "sacrifice two Blood tokens" is a cost, "only as a sorcery" its timing (CR 602.5d)
    @Test
    fun bloodthirstyLurker() = cardFaces(Layout.Transform,
        card(
            name = "Bloodthirsty Lurker",
            cost = "{1}{B}",
            type = "Creature — Vampire",
            text = """
                At the beginning of your end step, if you gained life this turn, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                Sacrifice two Blood tokens: Transform this creature. Activate only as a sorcery.
            """,
            pt = "1/3",
        ) to abilities(
            triggered(yourEndStep, create(blood), interveningIf = gainedLifeThisTurn),
            Ability.Activated(cost(CostPart.Sacrifice(CostObjects.Choose(and(sub("Blood"), token), n(2)))), Instructions(steps = listOf(transformSelf)), asSorcery),
        ),
        card(
            name = "Bloodsoaked Courtier",
            cost = null,
            type = "Creature — Vampire",
            text = """
                At the beginning of your end step, if you gained life this turn, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                {4}{B}: Each opponent loses 2 life and you gain 2 life.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            triggered(yourEndStep, create(blood), interveningIf = gainedLifeThisTurn),
            activated(manaCost("{4}{B}"), loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
        ),
    )

    // CR 701.27a, 509.1b (restriction on blockers by power), 510.2
    @Test
    fun howlingHunter() = cardFaces(Layout.Transform,
        card(
            name = "Howling Hunter",
            cost = "{2}{G}",
            type = "Creature — Werewolf Horror",
            text = """
                Creatures with power less than this creature's power can't block it.
                {5}{G}: Transform this creature.
            """,
            pt = "3/1",
        ) to abilities(
            smallerCantBlock,
            activated(manaCost("{5}{G}"), transformSelf),
        ),
        card(
            name = "Chorus Hunter",
            cost = null,
            type = "Creature — Eldrazi Werewolf",
            text = """
                Creatures with power less than this creature's power can't block it.
                Whenever this creature deals combat damage to a player, create a 3/2 colorless Eldrazi Horror creature token.
            """,
            pt = "3/5",
            colorIndicator = "G",
        ) to abilities(
            smallerCantBlock,
            triggered(combatDamageToAPlayer, create(creatureToken("Eldrazi Horror", emptyList(), 3, 2))),
        ),
    )

    // CR 701.27a; "up to two target creatures" with damage divided as 1 each (CR 120.3)
    @Test
    fun smolderingBrute() = cardFaces(Layout.Transform,
        card(
            name = "Smoldering Brute",
            cost = "{2}{R}{R}",
            type = "Creature — Werewolf Horror",
            text = """
                When this creature enters, it deals 1 damage to each of up to two target creatures.
                {4}{R}{R}: Transform this creature.
            """,
            pt = "3/2",
        ) to abilities(
            triggered(entersSelf, damage(1, Recipients(objects = target("creatures"))), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
            activated(manaCost("{4}{R}{R}"), transformSelf),
        ),
        card(
            name = "Raging Dreadwolf",
            cost = null,
            type = "Creature — Eldrazi Werewolf",
            text = "Whenever this creature attacks, it deals 2 damage to any target.",
            pt = "6/4",
            colorIndicator = "R",
        ) to abilities(
            triggered(attacksSelf, damage(2, toAny), targets = listOf(anyTarget)),
        ),
    )

    // CR 701.27a, 122.1; the ice counters come from the front face's replacement effect
    @Test
    fun thingBeneathTheIce() = cardFaces(Layout.Transform,
        card(
            name = "Thing Beneath the Ice",
            cost = "{1}{U}",
            type = "Creature — Horror",
            text = """
                Defender
                This creature enters with four ice counters on it.
                Whenever you cast an instant or sorcery spell, remove an ice counter from this creature. Then if it has no ice counters on it, transform it.
            """,
            pt = "0/4",
        ) to abilities(
            Keyword.Defender,
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(arcanum.api.Modification.EntersWithCounters(ice, n(4))))), affects = arcanum.api.Affected.Objects(Objects.Self)),
            triggered(youCast(or(instant, sorcery)), Step.Do(Effect.RemoveCounters(Objects.Self, ice, amount = n(1))),
                ifThen(Condition.ObjectMatches(Objects.Self, not(ObjectFilter.HasCounter(ice, atLeast = 1))), transformSelf)),
        ),
        card(
            name = "Woken Abyss",
            cost = null,
            type = "Creature — Kraken Horror",
            text = "When this creature transforms into Woken Abyss, return all non-Horror creatures to their owners' hands.",
            pt = "7/8",
            colorIndicator = "U",
        ) to abilities(
            triggered(Trigger.On(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Woken Abyss")))),
                move(Objects.Each(and(not(sub("Horror")), creature)), Zone.Hand)),
        ),
    )

    // CR 701.27a, 602.5 (tapping another creature is part of the cost: it needn't be able to pay {T} abilities, 302.6 doesn't apply)
    @Test
    fun villageBusybody() = cardFaces(Layout.Transform,
        card(
            name = "Village Busybody",
            cost = "{W}",
            type = "Creature — Human",
            text = "{T}, Tap an untapped creature you control: Transform this creature.",
            pt = "1/1",
        ) to abilities(
            activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(creature, n(1)))), transformSelf),
        ),
        card(
            name = "Angry Mob",
            cost = null,
            type = "Creature — Human",
            text = """
                This creature attacks each combat if able.
                {2}: This creature gets +1/+0 until end of turn.
            """,
            pt = "2/3",
            colorIndicator = "W",
        ) to abilities(
            static(Mechanic.MustAttack()),
            activated(manaCost("{2}"), pump(Objects.Self, 1, 0)),
        ),
    )

    // CR 701.27a; the trigger fires for the Blood token being created (CR 111.2), and the intervening "if" counts them
    @Test
    fun bloodSummoner() = cardFaces(Layout.Transform,
        card(
            name = "Blood Summoner",
            cost = "{1}{B}",
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                Whenever this creature or another nontoken creature you control dies, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                Whenever you create a Blood token, if you control five or more Blood tokens, transform this creature.
            """,
            pt = "2/1",
        ) to abilities(
            Keyword.Flying,
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, not(token), creature, you))), create(blood)),
            triggered(on(EventPattern.CreateToken(and(sub("Blood"), ObjectFilter.OwnedBy(PlayerFilter.You)))), transformSelf,
                interveningIf = Condition.Exists(and(sub("Blood"), token, you), atLeast = 5)),
        ),
        card(
            name = "Bat Summoner",
            cost = null,
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                At the beginning of combat on your turn, up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in addition to its other types.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            triggered(beginningOfCombatOnYourTurn, batBlood, targets = listOf(slot("token", and(sub("Blood"), token, you), min = 0))),
        ),
    )

    // CR 603.4, 701.27a; the counter is put first, and "then if" counts it
    @Test
    fun weddingBanquet() = cardFaces(Layout.Transform,
        card(
            name = "Wedding Banquet",
            cost = "{2}{W}",
            type = "Enchantment",
            text = "At the beginning of your end step, put an invitation counter on this enchantment. If you attacked with two or more creatures this turn, draw a card. Otherwise, create a 1/1 white Human creature token. Then if this enchantment has three or more invitation counters on it, transform it.",
        ) to abilities(
            triggered(yourEndStep, counters(Objects.Self, 1, invitation),
                ifThen(attackedWithTwo, draw(1), otherwise = listOf(create(creatureToken("Human", listOf(Color.White), 1, 1)))),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(invitation, atLeast = 3)), transformSelf)),
        ),
        card(
            name = "Wedding Feast",
            cost = null,
            type = "Enchantment",
            text = "Creatures you control get +1/+1.",
            colorIndicator = "W",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(creature, you))),
        ),
    )
}
