package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN cards whose first unread clause was an effect (chunk 08). The invented names carry the oracle text of the real cards. */
class FdnChunk08EffectsTest {
    private val enteringSelf = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private fun asEnters(vararg steps: Step) = static(Mechanic.Replacement(enteringSelf, modify = listOf(Modification.AsEnters(steps.toList()))))

    // CR 614.12 (as it enters), CR 201.3 (any card name), CR 607.2d (kept on it); the second line as Pithing-Needle-like texts read
    @Test
    fun pryingMonocle() = card(
        name = "Prying Monocle",
        cost = "{2}",
        type = "Artifact",
        text = """
            As this artifact enters, look at an opponent's hand, then choose any card name.
            Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        asEnters(
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent"))))), Players.You)),
            Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You, remember = true),
        ),
        static(Mechanic.CantBeActivated(manaAbilities = false), affects = Objects.Each(and(ObjectFilter.RememberedName("name"), ObjectFilter.Or(listOf(
            Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command).map { ObjectFilter.InZone(it) })))),
    )

    // CR 205.1a: "becomes a Werewolf" replaces its creature types; CR 611.2a: with no duration it lasts; CR 602.5b (only once)
    @Test
    fun bashfulArchivist() = card(
        name = "Bashful Archivist",
        cost = "{G}",
        type = "Creature — Human",
        pt = "1/1",
        text = "{3}{G}: This creature becomes a Werewolf. Put two +1/+1 counters on it and you draw a card. Activate only once.",
    ) parsesTo abilities(
        activated(manaCost("{3}{G}"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Werewolf"))), replaceSubtypes = true)),
                Affected.Objects(Objects.Self), Duration.Indefinitely)),
            counters(Objects.Self, 2),
            draw(1),
            rules = anyTime.copy(once = true),
        ),
    )

    // CR 601.2f: either kind of spell costs {1} less
    @Test
    fun hillbannerCaptain() = card(
        name = "Hillbanner Captain",
        cost = "{1}{W}",
        type = "Creature — Kithkin Soldier",
        pt = "2/1",
        text = "Kithkin spells and Soldier spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(or(and(sub("Kithkin"), spellFilter, you), and(sub("Soldier"), spellFilter, you)), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 601.2b, 601.2f: an optional additional {2}; paid, it may be cast any time an instant could (CR 702.8a)
    @Test
    fun heraldOfTheEbb() = card(
        name = "Herald of the Ebb",
        cost = "{U}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "2/2",
        text = """
            You may cast this spell as though it had flash if you pay {2} more to cast it. (You may cast it any time you could cast an instant.)
            When this creature enters, you may return target tapped creature an opponent controls to its owner's hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(mana("{2}")), optional = true, flash = true)),
        triggered(entersSelf, Step.May(listOf(move(target("creature"), Zone.Hand)), Players.You),
            targets = listOf(slot("creature", and(tapped, creature, opponents)))),
    )

    // CR 118.9: casting without paying the mana cost; CR 601.3 (any card in your hand that can be cast)
    @Test
    fun boundlessInsight() = card(
        name = "Boundless Insight",
        cost = "{7}{U}{U}{U}",
        type = "Enchantment",
        text = "You may cast spells from your hand without paying their mana costs.",
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Hand, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 510.2, 120.3 (that many: the damage dealt); CR 601.2b, 601.2h: removing counters is an additional cost, spread as you choose
    @Test
    fun spinebristleWurm() = card(
        name = "Spinebristle Wurm",
        cost = "{4}{G}{G}",
        type = "Creature — Wurm",
        pt = "7/7",
        text = """
            Trample
            Whenever a creature you control deals combat damage during your turn, put that many +1/+1 counters on it. (It must survive to get the counters.)
            You may cast this card from your graveyard by removing six counters from among creatures you control in addition to paying its other costs.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(Trigger.On(EventPattern.Damage(creatureYouControl, arcanum.api.RecipientFilter(), combat = true)),
            Instructions(steps = listOf(counters(Objects.EventObject(EventRole.Source), Value.EventAmount))), triggersOnlyIf = Condition.YourTurn),
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You, additionalCost = cost(
            CostPart.RemoveCounters(null, n(6), CostObjects.Choose(creatureYouControl, count = null), distributed = true)))),
    )

    // CR 106.6: the mana may pay for a creature spell of the chosen type or an activated ability of a creature of that type
    @Test
    fun hiddenCloister() = card(
        name = "Hidden Cloister",
        cost = null,
        type = "Land",
        text = """
            As this land enters, choose a creature type.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type or activate an ability of a creature source of the chosen type.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Choose(n(1)), restriction = ObjectFilter.Or(listOf(
            and(spellFilter, creature, ObjectFilter.Sub(SubtypeRef.Remembered("type"))),
            ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = and(creature, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))))), Players.You))),
    )

    // CR 118.14, 609.4b: mana of any type, only for creature spells
    @Test
    fun stewardOfThePens() = card(
        name = "Steward of the Pens",
        cost = "{3}{G}",
        type = "Creature — Snake Cleric",
        pt = "3/4",
        text = """
            You may look at the top card of your library any time.
            You may cast creature spells from the top of your library.
            You can spend mana of any type to cast creature spells.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(creature, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        playerStatic(Mechanic.SpendManaAsAnyColor(onlyFor = and(spellFilter, creature), anyType = true)),
    )

    // CR 606.4 (loyalty abilities); "Choose one" of the three exiled cards, then CR 601.2 / 305.1 (play it this turn);
    // CR 707.9b, 111.10 (a copy with exceptions); CR 601.2d (damage divided among any number of targets)
    @Test
    fun pyrraBlazeshaper() = card(
        name = "Pyrra, Blazeshaper",
        cost = "{5}{R}{R}",
        type = "Legendary Planeswalker — Chandra",
        loyalty = "6",
        text = """
            [+2]: Add {R}{R}{R}. Exile the top three cards of your library. Choose one. You may play that card this turn.
            [+1]: Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
            [−4]: Pyrra deals 8 damage divided as you choose among any number of target creatures and/or planeswalkers.
        """,
    ) parsesTo abilities(
        loyalty(2,
            doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{R}{R}{R}")!!)), Players.You)),
            move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("chosen")),
                Duration.EndOfTurn)),
        ),
        loyalty(1,
            doing(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(target("creature"), except = listOf(arcanum.api.CopyException.AddAbilities(listOf(Keyword.Haste,
                triggered(stepBegins(TurnStep.End, PlayerFilter.Any), sacrificeIt))))), n(1), Players.You)),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
        loyalty(-4,
            damage(Value.DividedShare, toObjects(target("planeswalkers"))),
            targets = listOf(slot("planeswalkers", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)), max = null).copy(divided = n(8))),
        ),
    )

    // CR 702.40a, 707.10: as storm counts, but only your instants and sorceries cast before it; CR 707.10c (new targets)
    @Test
    fun endlessTempest() = card(
        name = "Endless Tempest",
        cost = "{4}{U}{R}",
        type = "Enchantment",
        text = "Whenever you cast an instant or sorcery spell, copy it for each other instant and sorcery spell you've cast before it this turn. You may choose new targets for the copies.",
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)), Step.Repeat(
            Value.SpellsCastBefore(Objects.EventObject(EventRole.Object), spells = or(instant, sorcery), caster = PlayerFilter.You),
            listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true))))),
    )

    // CR 614.1c, 614.12: each other Angel enters with a counter per Angel you control as it enters (not itself)
    @Test
    fun seraphelWellspringOfFaith() = card(
        name = "Seraphel, Wellspring of Faith",
        cost = "{1}{W}",
        type = "Legendary Creature — Angel",
        pt = "2/2",
        text = """
            Flying, vigilance
            Each other Angel you control enters with an additional +1/+1 counter on it for each Angel you already control.
            {T}: Add {W}. Spend this mana only to cast an Angel spell.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(another, sub("Angel"), you), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, count(and(sub("Angel"), you)))))),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{W}")!!),
            restriction = and(spellFilter, sub("Angel"))), Players.You))),
    )

    // CR 603.10a (as it last existed), 603.4 (intervening if); CR 603.7 (a delayed trigger), 400.7 (the card is a new object)
    @Test
    fun ninefoldTabby() = card(
        name = "Ninefold Tabby",
        cost = "{1}{B}{B}",
        type = "Creature — Cat",
        pt = "1/1",
        text = """
            This creature enters with eight revival counters on it if you cast it.
            When this creature dies, if it had a revival counter on it, return it to the battlefield with one fewer revival counter on it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(enteringSelf, modify = listOf(Modification.EntersWithCounters(revival, n(8)))), condition = Condition.CastFrom(null, Objects.Self)),
        triggered(diesSelf,
            Step.Bind(Objects.EventObject(EventRole.After), "dead"),
            Step.Bind(Objects.EventObject(EventRole.Before), "was"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                doing(Effect.Move(Objects.Bound("dead"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(revival,
                    Value.Difference(Value.Of(NumericProperty.Counters(revival), Objects.Bound("was"), Combine.Single), n(1))))), postBind = "returned")))))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(revival, atLeast = 1)),
        ),
    )

    // CR 120.4a: excess damage, beyond lethal for the creature
    @Test
    fun goblinHaggling() = card(
        name = "Goblin Haggling",
        cost = "{X}{R}{R}",
        type = "Sorcery",
        text = "Goblin Haggling deals X damage to target creature. Create a number of 1/1 red Goblin creature tokens equal to the amount of excess damage dealt to that creature this way.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("creature")), Value.Announced(name = "X"), Objects.Self, bind = "damage")),
            create(creatureToken("Goblin", listOf(Color.Red), 1, 1), Value.Excess("damage")),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
    )

    // CR 201.2 (other creatures with this name); CR 100.2a (any number in a deck)
    @Test
    fun heirOfTheWarren() = card(
        name = "Heir of the Warren",
        cost = "{1}{W}",
        type = "Creature — Rabbit Noble",
        pt = "2/2",
        text = """
            When this creature enters, create a number of 1/1 white Rabbit creature tokens equal to the number of other creatures you control named Heir of the Warren.
            A deck can have any number of cards named Heir of the Warren.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Rabbit", listOf(Color.White), 1, 1),
            count(and(another, creature, you, ObjectFilter.Named("Heir of the Warren"))))),
        Ability.Static(listOf(Mechanic.AnyNumberInDeck), affects = Affected.Objects(Objects.Self)),
    )

    // CR 120.3 (that many: the damage dealt); CR 602.2b, 701.26a (tapping untapped Elves you control as a cost)
    @Test
    fun vaelithEdgeOfTheElves() = card(
        name = "Vaelith, Edge of the Elves",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Elf Noble",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever Vaelith deals combat damage to a player, create that many 1/1 green Elf Warrior creature tokens.
            {T}, Tap ten untapped Elves you control: Each opponent loses 10 life and you gain 10 life.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer, create(creatureToken("Elf Warrior", listOf(Color.Green), 1, 1), Value.EventAmount)),
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(sub("Elf"), count = n(10)))),
            loseLife(10, Players.Each(PlayerFilter.Opponent)), gainLife(10)),
    )

    // CR 110.4 (the permanent types), 701.21a (sacrifice); each opponent keeps one of each type they have, a permanent of several types
    // possibly kept as each of them
    @Test
    fun morwenGravetideSovereign() = card(
        name = "Morwen, Gravetide Sovereign",
        cost = "{4}{B}{B}",
        type = "Legendary Planeswalker — Liliana",
        loyalty = "6",
        text = """
            Whenever a creature you control dies, draw a card.
            [+1]: Create a 2/2 black Zombie creature token.
            [−4]: Each player sacrifices two creatures of their choice.
            [−9]: Each opponent chooses a permanent they control of each permanent type and sacrifices the rest.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)), draw(1)),
        loyalty(1, create(creatureToken("Zombie", listOf(Color.Black), 2, 2))),
        loyalty(-4, Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(creature, theirs), n(2), n(2)), bind = "sacrificed", chooser = Players.Bound("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
        loyalty(-9, Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "player", together = true, steps = keptOfEachType +
            doing(Effect.Sacrifice(Objects.Except(Objects.Each(and(permanentFilter, theirs)), Objects.Union(keptOfEachType.map { Objects.Bound(it.bind) })))))),
    )
    private val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))
    private val keptOfEachType = listOf("artifact" to arcanum.api.CardType.Artifact, "battle" to arcanum.api.CardType.Battle,
        "creature" to arcanum.api.CardType.Creature, "enchantment" to arcanum.api.CardType.Enchantment, "land" to arcanum.api.CardType.Land,
        "planeswalker" to arcanum.api.CardType.Planeswalker).map { (name, type) ->
        Step.Choose(Choice.Objects(and(ObjectFilter.Type(type), theirs), n(1), n(1)), bind = "kept $name", chooser = Players.Bound("player"))
    }

    private val revival = CounterKind.Named("revival")
}
