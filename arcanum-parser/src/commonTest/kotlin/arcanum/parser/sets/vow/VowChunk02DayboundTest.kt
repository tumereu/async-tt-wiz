package arcanum.parser.sets.vow

import arcanum.api.Ability
import arcanum.parser.cube.anyOneColor
import arcanum.api.CostChange
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.DamageOccurrence
import arcanum.api.RecipientFilter
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
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
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/** VOW chunk 02: Daybound and Nightbound (CR 702.145) on transforming double-faced cards. Invented names, the real cards' oracle text. */
class VowChunk02DayboundTest {
    private val dayboundText = "Daybound (If a player casts no spells during their own turn, it becomes night next turn.)"
    private val nightboundText = "Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)"
    private val werewolf = or(sub("Wolf"), sub("Werewolf"))
    private val insect = creatureToken("Insect", listOf(Color.Green), 1, 1)
    private val damageToAny = damage(1, toAny)
    private val damagedCantBlock = doing(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)),
        Affected.Objects(Objects.Where(target("any"), ObjectFilter.Type(CardType.Creature))), Duration.EndOfTurn))

    // CR 702.145b, 702.145e: the two keywords; CR 115.3: "another target creature" is another creature you control
    @Test
    fun avabruckCaretaker() = cardFaces(Layout.Transform,
        card(
            name = "Thornwatch Keeper",
            cost = "{4}{G}{G}",
            type = "Creature — Human Werewolf",
            text = """
                Hexproof
                At the beginning of combat on your turn, put two +1/+1 counters on another target creature you control.
                $dayboundText
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Hexproof,
            triggered(beginningOfCombatOnYourTurn, counters(target("creature"), 2), targets = listOf(slot("creature", and(another, creature, you)))),
            Keyword.Daybound,
        ),
        card(
            name = "Thornwatch Alpha",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Hexproof
                Other permanents you control have hexproof.
                At the beginning of combat on your turn, put two +1/+1 counters on each creature you control.
                $nightboundText
            """,
            pt = "6/6",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Hexproof,
            Ability.Static(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Hexproof))),
                affects = Affected.Objects(Objects.Each(and(another, permanentFilter, you))),
            ),
            triggered(beginningOfCombatOnYourTurn, counters(Objects.Each(and(creature, you)), 2)),
            Keyword.Nightbound,
        ),
    )

    // CR 509.1b: a creature dealt damage this way can't block this turn (only the creature dealt damage by this ability)
    @Test
    fun ballistaWatcher() = cardFaces(Layout.Transform,
        card(
            name = "Bolt Lookout",
            cost = "{2}{R}{R}",
            type = "Creature — Human Soldier Werewolf",
            text = """
                {2}{R}, {T}: This creature deals 1 damage to any target.
                $dayboundText
            """,
            pt = "4/3",
        ) to abilities(
            activated(cost(mana("{2}{R}"), tap), damageToAny, targets = listOf(anyTarget)),
            Keyword.Daybound,
        ),
        card(
            name = "Bolt Marksman",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                {2}{R}: This creature deals 1 damage to any target. A creature dealt damage this way can't block this turn.
                $nightboundText
            """,
            pt = "5/5",
            colorIndicator = "R",
        ) to abilities(
            activated(manaCost("{2}{R}"), damageToAny, damagedCantBlock, targets = listOf(anyTarget)),
            Keyword.Nightbound,
        ),
    )

    // CR 115.1, 120.2: each "up to one" target is chosen independently and may be none; one damage event to every chosen recipient
    @Test
    fun volatileArsonist() {
        fun attackDamage(amount: Int) = triggered(attacksSelf,
            damage(amount, Recipients(objects = Objects.Union(listOf(target("creature"), target("planeswalker"))), players = Players.Target("player"))),
            targets = listOf(
                slot("creature", creature, min = 0),
                TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(0), max = n(1)),
                slot("planeswalker", ObjectFilter.Type(CardType.Planeswalker), min = 0),
            ))
        cardFaces(Layout.Transform,
            card(
                name = "Embershower Arsonist",
                cost = "{3}{R}{R}",
                type = "Creature — Human Werewolf",
                text = """
                    Menace, haste
                    Whenever this creature attacks, it deals 1 damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker.
                    $dayboundText
                """,
                pt = "4/4",
            ) to abilities(Keyword.Menace, Keyword.Haste, attackDamage(1), Keyword.Daybound),
            card(
                name = "Embershower Anarchist",
                cost = null,
                type = "Creature — Werewolf",
                text = """
                    Menace, haste
                    Whenever this creature attacks, it deals 2 damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker.
                    $nightboundText
                """,
                pt = "5/5",
                colorIndicator = "R",
            ) to abilities(Keyword.Menace, Keyword.Haste, attackDamage(2), Keyword.Nightbound),
        )
    }

    @Test
    fun childOfThePack() = cardFaces(Layout.Transform,
        card(
            name = "Cub of the Den",
            cost = "{2}{R}{G}",
            type = "Creature — Human Werewolf",
            text = """
                {2}{R}{G}: Create a 2/2 green Wolf creature token.
                $dayboundText
            """,
            pt = "2/5",
        ) to abilities(
            activated(manaCost("{2}{R}{G}"), create(creatureToken("Wolf", listOf(Color.Green), 2, 2))),
            Keyword.Daybound,
        ),
        card(
            name = "Den Packmate",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Trample
                Other creatures you control get +1/+0.
                $nightboundText
            """,
            pt = "5/5",
            colorIndicator = "RG",
        ) to abilities(
            Keyword.Trample,
            Ability.Static(listOf(pt(1, 0)), affects = Affected.Objects(Objects.Each(and(another, creature, you)))),
            Keyword.Nightbound,
        ),
    )

    @Test
    fun fearfulVillager() = cardFaces(Layout.Transform,
        card(
            name = "Timid Villager",
            cost = "{2}{R}",
            type = "Creature — Human Werewolf",
            text = """
                Menace (This creature can't be blocked except by two or more creatures.)
                $dayboundText
            """,
            pt = "2/3",
        ) to abilities(Keyword.Menace, Keyword.Daybound),
        card(
            name = "Frightful Werewolf",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Menace (This creature can't be blocked except by two or more creatures.)
                $nightboundText
            """,
            pt = "4/3",
            colorIndicator = "R",
        ) to abilities(Keyword.Menace, Keyword.Nightbound),
    )

    @Test
    fun hookhandMariner() = cardFaces(Layout.Transform,
        card(
            name = "Gaffhand Sailor",
            cost = "{3}{G}",
            type = "Creature — Human Werewolf",
            text = dayboundText,
            pt = "4/4",
        ) to abilities(Keyword.Daybound),
        card(
            name = "Gaffhook Raider",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                This creature can't be blocked by creatures with power 2 or less.
                $nightboundText
            """,
            pt = "6/4",
            colorIndicator = "G",
        ) to abilities(static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))), Keyword.Nightbound),
    )

    // CR 702.145c: the front face's activated ability is only for sorcery timing
    @Test
    fun howlpackPiper() = cardFaces(Layout.Transform,
        card(
            name = "Hornpack Caller",
            cost = "{3}{G}",
            type = "Creature — Human Werewolf",
            text = """
                This spell can't be countered.
                {1}{G}, {T}: You may put a creature card from your hand onto the battlefield. If it's a Wolf or Werewolf, untap this creature. Activate only as a sorcery.
                $dayboundText
            """,
            pt = "2/2",
        ) to abilities(
            static(Mechanic.CantBeCountered),
            activated(cost(mana("{1}{G}"), tap),
                Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "returned"),
                ifThen(Condition.ObjectMatches(Objects.Bound("returned"), werewolf), untapIt(Objects.Self)),
                rules = asSorcery),
            Keyword.Daybound,
        ),
        card(
            name = "Hornpack Howler",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever this creature enters or transforms into Hornpack Howler, look at the top six cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
                $nightboundText
            """,
            pt = "4/4",
            colorIndicator = "G",
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Hornpack Howler"))))),
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
            Keyword.Nightbound,
        ),
    )

    // CR 120.3, 603.2: "that much damage" is the damage dealt to it; the back face triggers for any permanent you control
    @Test
    fun illTemperedLoner() = cardFaces(Layout.Transform,
        card(
            name = "Sour Loner",
            cost = "{2}{R}{R}",
            type = "Creature — Human Werewolf",
            text = """
                Whenever this creature is dealt damage, it deals that much damage to any target.
                {1}{R}: This creature gets +2/+0 until end of turn.
                $dayboundText
            """,
            pt = "3/3",
        ) to abilities(
            Ability.Triggered(
                Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), per = DamageOccurrence.Recipient)),
                Instructions(targets = listOf(anyTarget), steps = listOf(damage(Value.EventAmount, toAny))),
            ),
            activated(manaCost("{1}{R}"), pump(Objects.Self, 2, 0)),
            Keyword.Daybound,
        ),
        card(
            name = "Howlpack Avenger Stand-in",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever a permanent you control is dealt damage, this creature deals that much damage to any target.
                {1}{R}: This creature gets +2/+0 until end of turn.
                $nightboundText
            """,
            pt = "4/4",
            colorIndicator = "R",
        ) to abilities(
            Ability.Triggered(
                Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(permanentFilter, you)), per = DamageOccurrence.Recipient)),
                Instructions(targets = listOf(anyTarget), steps = listOf(damage(Value.EventAmount, toAny))),
            ),
            activated(manaCost("{1}{R}"), pump(Objects.Self, 2, 0)),
            Keyword.Nightbound,
        ),
    )

    @Test
    fun infestationExpert() = cardFaces(Layout.Transform,
        card(
            name = "Pest Warden",
            cost = "{4}{G}",
            type = "Creature — Human Werewolf",
            text = """
                Whenever this creature enters or attacks, create a 1/1 green Insect creature token.
                $dayboundText
            """,
            pt = "3/4",
        ) to abilities(triggered(entersOrAttacksSelf, create(insect)), Keyword.Daybound),
        card(
            name = "Pest Werewolf",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever this creature enters or attacks, create two 1/1 green Insect creature tokens.
                $nightboundText
            """,
            pt = "4/5",
            colorIndicator = "G",
        ) to abilities(triggered(entersOrAttacksSelf, create(insect, count = n(2))), Keyword.Nightbound),
    )

    @Test
    fun lambholtRaconteur() = cardFaces(Layout.Transform,
        card(
            name = "Pasture Storyteller",
            cost = "{3}{R}",
            type = "Creature — Human Werewolf",
            text = """
                Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent.
                $dayboundText
            """,
            pt = "2/4",
        ) to abilities(triggered(youCast(noncreatureSpell), damage(1, eachOpponent)), Keyword.Daybound),
        card(
            name = "Pasture Ravager",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever you cast a noncreature spell, this creature deals 2 damage to each opponent.
                $nightboundText
            """,
            pt = "4/4",
            colorIndicator = "R",
        ) to abilities(triggered(youCast(noncreatureSpell), damage(2, eachOpponent)), Keyword.Nightbound),
    )

    // CR 601.2b, 118.9: flash only if the optional extra {2} is paid
    @Test
    fun oakshadeStalker() = cardFaces(Layout.Transform,
        card(
            name = "Glade Prowler",
            cost = "{2}{G}",
            type = "Creature — Human Ranger Werewolf",
            text = """
                You may cast this spell as though it had flash if you pay {2} more to cast it.
                $dayboundText
            """,
            pt = "3/3",
        ) to abilities(static(Mechanic.AdditionalCost(cost = cost(mana("{2}")), optional = true, flash = true)), Keyword.Daybound),
        card(
            name = "Glade Ambusher",
            cost = null,
            type = "Creature — Werewolf",
            text = nightboundText,
            pt = "6/3",
            colorIndicator = "G",
        ) to abilities(Keyword.Nightbound),
    )

    // CR 702.145d: Defender front, attacks each combat (CR 508.1d) back
    @Test
    fun wearyPrisoner() = cardFaces(Layout.Transform,
        card(
            name = "Tired Captive",
            cost = "{3}{R}",
            type = "Creature — Human Werewolf",
            text = """
                Defender
                $dayboundText
            """,
            pt = "2/6",
        ) to abilities(Keyword.Defender, Keyword.Daybound),
        card(
            name = "Furious Escapee",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                This creature attacks each combat if able.
                $nightboundText
            """,
            pt = "6/6",
            colorIndicator = "R",
        ) to abilities(static(Mechanic.MustAttack()), Keyword.Nightbound),
    )

    // CR 106.1b, 605.1a: mana abilities; "two mana of any one color" is one color chosen for both
    @Test
    fun weaverOfBlossoms() = cardFaces(Layout.Transform,
        card(
            name = "Bloomstitcher",
            cost = "{2}{G}",
            type = "Creature — Human Werewolf",
            text = """
                {T}: Add one mana of any color.
                $dayboundText
            """,
            pt = "2/3",
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
            Keyword.Daybound,
        ),
        card(
            name = "Bloomclad Werewolf",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                {T}: Add two mana of any one color.
                $nightboundText
            """,
            pt = "3/4",
            colorIndicator = "G",
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(2)), Players.You))),
            Keyword.Nightbound,
        ),
    )

    // CR 601.2f: cost reduction by a condition; CR 700.4: "dies" for this or another Wolf or Werewolf you control
    @Test
    fun wolfkinOutcast() = cardFaces(Layout.Transform,
        card(
            name = "Packless Outcast",
            cost = "{5}{G}",
            type = "Creature — Human Werewolf",
            text = """
                This spell costs {2} less to cast if you control a Wolf or Werewolf.
                $dayboundText
            """,
            pt = "5/4",
        ) to abilities(
            static(
                Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1))),
                condition = Condition.Exists(filter = and(werewolf, you), atLeast = 1),
            ),
            Keyword.Daybound,
        ),
        card(
            name = "Packless Crasher",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever this creature or another Wolf or Werewolf you control dies, draw a card.
                $nightboundText
            """,
            pt = "6/5",
            colorIndicator = "G",
        ) to abilities(
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, werewolf, you))), draw(1)),
            Keyword.Nightbound,
        ),
    )
}
