package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 13: black and red cards. Mana only for Equipment spells and equip abilities (CR 106.6); a sacrifice of an
 * Equipment attached to it; a returned Villain that is also a Hero (CR 611.2e); a spell costing less with a Villain; "if he's equipped"
 * (CR 603.4) and X by the Equipment attached; a land destroyed with a search by its controller; the top card exiled and playable until
 * your next turn ends; control until the end of your next turn by a Villain's mana value; "you may pay {1} up to three times. When
 * you do, choose up to that many —" (CR 603.12, 700.2); noncombat damage plus X (CR 614.1a); the equipped creature dealing damage
 * as it becomes tapped (CR 120.1).
 */
class MshChunk13BlackRedTest {
    // CR 106.6: spent only on Equipment spells and equip abilities
    @Test
    fun wanderingBladeShadowStalker() = card(
        name = "Wandering Blade, Shadow Stalker",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Rogue Hero",
        text = """
            Pay 2 life: Add two mana of any one color. Spend this mana only to cast Equipment spells or activate equip abilities. Activate only once each turn.
            {T}, Sacrifice an Equipment attached to Wandering Blade: Target creature gets -4/-4 until end of turn. Activate only as a sorcery.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.PayLife(n(2)))), Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2)),
            restriction = or(and(spellFilter, equipment), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, keyword = Keyword.Equip.Kind))), Players.You)))),
            anyTime.copy(perTurn = 1)),
        activated(cost(tap, CostPart.Sacrifice(CostObjects.Choose(and(equipment, ObjectFilter.Attached(to = Objects.Self)), count = n(1)))),
            pump(target("creature"), -4, -4), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    @Test
    fun petrocorpBrutes() = card(
        name = "Petrocorp Brutes",
        cost = "{4}{B}",
        type = "Creature — Human Berserker Villain",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever you draw your second card each turn, put a +1/+1 counter on target creature.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(secondCardEachTurn, counters(target("creature")), targets = listOf(slot("creature", creature))),
        Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{2}")),
    )

    @Test
    fun stolenSteeleTech() = card(
        name = "Stolen Steele Tech",
        cost = "{1}{B}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
            Equipped creature gets +1/+0.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), gains(target("creature"), Keyword.Indestructible),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 0), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}")),
    )

    @Test
    fun superShifter() = card(
        name = "Super-Shifter",
        cost = "{1}{B}{B}{B}",
        type = "Legendary Creature — Skrull Shapeshifter Villain",
        text = """
            Flying
            {2}{W}: Create a 0/4 colorless Wall creature token with defender.
            {3}{G}: Super-Shifter gets +4/+4 until end of turn.
            {4}{R}: Super-Shifter deals 4 damage to target creature.
            {5}{U}: Target player draws four cards.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{2}{W}"), create(creatureToken("Wall", emptyList(), 0, 4, abilities = listOf(Keyword.Defender)))),
        activated(manaCost("{3}{G}"), pump(Objects.Self, 4, 4)),
        activated(manaCost("{4}{R}"), damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
        activated(manaCost("{5}{U}"), draw(4, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 611.2e: the returned creature is a Hero as it enters
    @Test
    fun lightningSquadConspiracy() = card(
        name = "Lightning Squad Conspiracy",
        cost = "{3}{B}",
        type = "Enchantment",
        text = """
            Flash
            Whenever a Villain you control dies, return it to the battlefield under its owner's control with a finality counter on it. That creature is a Hero in addition to its other types. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(on(EventPattern.ZoneChange(and(villainFilter, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)),
                counters = listOf(arcanum.api.CountersToPut(CounterKind.Finality, n(1))),
                entersAs = listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Hero")))))), postBind = "returned"))),
    )

    @Test
    fun visionsOfInfamy() = card(
        name = "Visions of Infamy",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Villain.
            You draw two cards and lose 2 life.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
            affects = arcanum.api.Affected.Objects(Objects.Self), condition = Condition.Exists(and(villainFilter, you), atLeast = 1)),
        spell(draw(2), loseLife(2)),
    )

    // CR 603.4: "if he's equipped"; X counts the Equipment attached to him
    @Test
    fun lashVengefulEngineer() = card(
        name = "Lash, Vengeful Engineer",
        cost = "{B}",
        type = "Legendary Creature — Human Artificer Villain",
        text = """
            Lash enters tapped.
            Whenever Lash attacks, if he's equipped, each opponent loses X life and you gain X life, where X is the number of Equipment attached to him.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        triggered(attacksSelf, Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), attachedEquipment)), Step.Do(Effect.GainLife(Players.You, attachedEquipment)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(attachments = equipment))),
    )

    // CR 700.2: modes; the land's controller searches (CR 701.23)
    @Test
    fun guardiansDisbanded() = card(
        name = "Guardians Disbanded",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Guardians Disbanded deals 3 damage to each creature.
            • Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(Objects.Each(creature)))),
            mode(destroy(target("land")), Step.May(listOf(
                Step.Do(Effect.Search(Players.ControllerOf(target("land")), basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found",
                    searcher = Players.ControllerOf(target("land")))),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true, controller = Players.ControllerOf(target("land"))),
                Step.Do(Effect.Shuffle(Players.ControllerOf(target("land"))))), player = Players.ControllerOf(target("land"))),
                targets = listOf(slot("land", land))),
            max = 2))),
    )

    @Test
    fun fieryCrescendo() = card(
        name = "Fiery Crescendo",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +3/+1 until end of turn.
            Exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 1), exileTopPlayable, playable, targets = listOf(slot("creature", creature))),
    )

    @Test
    fun scarletOperative() = card(
        name = "Scarlet Operative",
        cost = "{3}{R}",
        type = "Artifact Creature — Human Villain",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Prowess,
        triggered(entersSelf, exileTopPlayable, playable),
    )

    // control until the end of your next turn instead, by a Villain's greater mana value as it resolves
    @Test
    fun inEvilsGrip() = card(
        name = "In Evil's Grip",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. If you control a Villain with greater mana value than that creature, gain control of that creature until the end of your next turn instead. Untap that creature. It gains haste until end of turn.",
    ) parsesTo abilities(
        spell(
            Step.If(Condition.Exists(and(villainFilter, you, ObjectFilter.ManaValue(arcanum.api.Comparison.Greater,
                    Value.Of(arcanum.api.NumericProperty.ManaValue, target("creature"), arcanum.api.Combine.Single))), atLeast = 1),
                then = listOf(control(Duration.UntilEndOfYourNextTurn)), otherwise = listOf(control(Duration.EndOfTurn))),
            untapIt(target("creature")), gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.12, 700.2: the number of times paid is the most modes chosen; flavor words name the modes (CR 207.2d)
    @Test
    fun fletcherMasterMarksman() = card(
        name = "Fletcher, Master Marksman",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Archer Hero",
        text = """
            Reach, first strike
            Trick Arrows — Whenever Fletcher becomes tapped, you may pay {1} up to three times. When you do, choose up to that many —
            • Net — Target creature can't block this turn.
            • Explosive — Fletcher deals 2 damage to target player.
            • Boomerang — Discard a card, then draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.FirstStrike,
        Ability.Triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
            Step.Choose(Choice.Number(n(1), n(3)), bind = "times", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!, times = Value.Bound("times")))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(modes = Modes(listOf(
                // the reflexive trigger's modes keep their printed lines (CR 700.2)
                mode(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creature", creature)))
                    .copy(text = "Net — Target creature can't block this turn."),
                mode(damage(2, Recipients(players = Players.Target("player"))), targets = listOf(targetPlayer))
                    .copy(text = "Explosive — Fletcher deals 2 damage to target player."),
                mode(discard(1), draw(1)).copy(text = "Boomerang — Discard a card, then draw a card.")),
                min = 0, max = 3, chooser = Players.You, maxCount = Value.Bound("times"))))))))),
            abilityWord = "Trick Arrows"),
    )

    // CR 614.1a: noncombat damage to opponents and their permanents increased by its power
    @Test
    fun fletcherYoungGuardian() = card(
        name = "Fletcher, Young Guardian",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Archer Hero",
        text = """
            Reach
            If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, instead it deals that much damage plus X, where X is Fletcher's power.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Reach,
        static(Mechanic.Replacement(EventPattern.Damage(you, RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent), combat = false),
            modify = listOf(Modification.AddAmount(power(Objects.Self))))),
    )

    // CR 120.1: the equipped creature is the source of the damage
    @Test
    fun fletchersBow() = card(
        name = "Fletcher's Bow",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has reach.
            Whenever equipped creature becomes tapped, it deals 1 damage to each opponent.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Reach), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))),
            damage(1, eachOpponent, source = Objects.EventObject(EventRole.Object))),
        Keyword.Equip(manaCost("{1}")),
    )

    private val attachedEquipment = count(and(equipment, ObjectFilter.Attached(to = Objects.Self)))
    private val exileTopPlayable = move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled")
    private val playable = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))
    private fun control(duration: Duration) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)),
        arcanum.api.Affected.Objects(target("creature")), duration))
}
