package arcanum.parser.sets.rvr

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk03ReplicateSurveilAndConditionsTest {
    private fun surveil(count: Int): Step = Step.Do(Effect.Surveil(Players.You, n(count)))
    private fun withX(ability: Ability.Activated) = ability.copy(instructions = ability.instructions.copy(announced = listOf("X")))
    private val replicateReminder = "(When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)"

    // ---- Replicate

    // CR 702.56a: replicate; CR 205.1b, 611.2a: it becomes a 3/1 creature for good and keeps its land types
    @Test
    fun rampartAwakening() = card(
        name = "Rampart Awakening",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Replicate {1}{R} $replicateReminder
            Target Mountain becomes a 3/1 creature. It's still a land.
        """,
    ) parsesTo abilities(
        Keyword.Replicate(manaCost("{1}{R}")),
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.SetPowerToughness(n(3), n(1))),
                arcanum.api.Affected.Objects(target("Mountain")), Duration.Indefinitely)),
            targets = listOf(slot("Mountain", sub("Mountain"))),
        ),
    )

    // CR 702.56a
    @Test
    fun shardFrenzy() = card(
        name = "Shard Frenzy",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Replicate {R} $replicateReminder
            Destroy target artifact.
        """,
    ) parsesTo abilities(
        Keyword.Replicate(manaCost("{R}")),
        spell(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
    )

    // ---- Surveil

    // CR 701.25a: surveil; CR 608.2d: the creature card is chosen as it resolves (not targeted)
    @Test
    fun contriveDecoct() = cardFaces(
        Layout.Split,
        card(name = "Contrive", cost = "{2}{U/B}{U/B}", type = "Sorcery", text = "Gain control of target creature with power 2 or less.") to abilities(
            spell(doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), arcanum.api.Affected.Objects(target("creature")), Duration.Indefinitely)),
                targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2)))))),
        card(name = "Decoct", cost = "{3}{U}{B}", type = "Sorcery", text = "Surveil 3, then return a creature card from your graveyard to the battlefield.") to abilities(
            spell(
                surveil(3),
                Step.Choose(Choice.Objects(inYourGraveyard(creature), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
            )),
    )

    // CR 707.9b: the copy's exceptions (its name, legendary, this ability); CR 707.2: it copies the card in the graveyard
    @Test
    fun vexinTheManifold() = card(
        name = "Vexin, the Manifold",
        cost = "{U}{B}",
        type = "Legendary Creature — Shapeshifter",
        pt = "1/3",
        text = """
            When Vexin enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {X}: Vexin becomes a copy of target creature card in your graveyard with mana value X, except its name is Vexin, the Manifold, it's legendary in addition to its other types, and it has this ability.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, surveil(1)),
        withX(activated(manaCost("{X}"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("card"), listOf(
                CopyException.Name("Vexin, the Manifold"),
                CopyException.Types(addSupertypes = listOf(Supertype.Legendary)),
                CopyException.ThisAbility,
            ))), arcanum.api.Affected.Objects(Objects.Self), Duration.Indefinitely)),
            targets = listOf(slot("card", and(creature, *yourGraveyard.toTypedArray(), ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))),
        )),
    )

    // CR 701.25a
    @Test
    fun noxiousHaze() = card(
        name = "Noxious Haze",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            All creatures get -1/-1 until end of turn.
            Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        spell(pump(Objects.Each(creature), -1, -1), surveil(2)),
    )

    // CR 701.6a, 701.25a
    @Test
    fun slySubversion() = card(
        name = "Sly Subversion",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Counter target spell.
            Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), surveil(1), targets = listOf(slot("spell", spellFilter))),
    )

    // ---- Conditions

    // CR 101.4: the player whose upkeep it is chooses; CR 701.21a: they can't sacrifice when they have no creature
    @Test
    fun dirgecallerFiend() = card(
        name = "Dirgecaller Fiend",
        cost = "{3}{B}{B}",
        type = "Creature — Demon",
        pt = "4/4",
        text = """
            Flying
            At the beginning of each player's upkeep, that player sacrifices a creature of their choice. If the player can't, sacrifice this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any),
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Active))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Active),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ifThen(Condition.Not(Condition.NonEmpty("sacrificed")), doing(Effect.Sacrifice(Objects.Self))),
        ),
    )

    // CR 614.1a: a replacement (draw two instead of one); CR 601.2a: cast from the library; CR 115.10: targeted by a spell only
    @Test
    fun wobblinTheStrayed() = card(
        name = "Wobblin, the Strayed",
        cost = "{1}{U}",
        type = "Legendary Creature — Homunculus",
        pt = "1/1",
        text = """
            When Wobblin enters, draw a card. If it entered from your library or was cast from your library, draw two cards instead.
            When Wobblin becomes the target of a spell, shuffle Wobblin into its owner's library.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            ifThen(Condition.ObjectMatches(Objects.Self, and(ObjectFilter.OwnedBy(PlayerFilter.You), or(
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Library, to = Zone.Battlefield), role = EventRole.After),
                ObjectFilter.WasCast(from = Zone.Library)))),
                draw(2), otherwise = listOf(draw(1))),
        ),
        triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = ObjectFilter.Category(ObjectCategory.Spell))),
            move(Objects.Self, Zone.Library),
            doing(Effect.Shuffle(Players.OwnerOf(Objects.Self))),
        ),
    )

    // CR 603.4: when this or another creature enters, both "it"s in the intervening if are the creature that entered
    @Test
    fun gravecrawlLarva() = card(
        name = "Gravecrawl Larva",
        cost = "{1}{B}{G}",
        type = "Creature — Insect",
        pt = "3/2",
        text = "Whenever this creature or another creature you control enters, if it entered from your graveyard or you cast it from your graveyard, draw a card.",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), draw(1),
            interveningIf = Condition.Or(listOf(
                Condition.ObjectMatches(Objects.EventObject(EventRole.After), and(ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Graveyard, to = Zone.Battlefield), role = EventRole.After))),
                Condition.CastFrom(Zone.Graveyard, Objects.EventObject(EventRole.After)),
            )),
        ),
    )

    // CR 614.1a: the damage is replaced, not prevented (so damage that can't be prevented is replaced too, CR 615.12)
    @Test
    fun mossgorgerHydra() = card(
        name = "Mossgorger Hydra",
        cost = "{2}{G}{W}{W}",
        type = "Creature — Plant Hydra",
        pt = "1/1",
        text = "If damage would be dealt to this creature, put that many +1/+1 counters on it instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))),
            instead = Instructions(steps = listOf(counters(Objects.Self, Value.EventAmount))))),
    )

    // CR 509.4: put onto the battlefield blocking the creature this one blocked (that creature, the other combatant); CR 603.12 "if you do"
    @Test
    fun driftveilPhantasm() = card(
        name = "Driftveil Phantasm",
        cost = "{2}{U}{U}",
        type = "Creature — Illusion",
        pt = "1/1",
        text = "Whenever this creature blocks a creature, you may return this creature to its owner's hand. If you do, you may put a creature card from your hand onto the battlefield blocking that creature.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Block(ObjectFilter.Is(Objects.Self), creature, perAttacker = true)),
            Step.Pay(Cost(listOf(CostPart.ReturnToHand(CostObjects.Self))), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"),
                Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = n(1)),
                    bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, blocking = Objects.EventObject(EventRole.OtherCombatant))))),
        ),
    )

    // CR 205.1b: a creature that's still a planeswalker (Human Soldier added, its own types kept); CR 615: prevention during your turn
    @Test
    fun tarrikDuskblade() = card(
        name = "Tarrik Duskblade",
        cost = "{1}{W}{W}",
        type = "Legendary Planeswalker — Gideon",
        loyalty = "4",
        text = """
            During your turn, Tarrik Duskblade is a 4/4 Human Soldier creature with indestructible that's still a planeswalker.
            Prevent all damage that would be dealt to Tarrik Duskblade during your turn.
            [+1]: Up to one other target creature you control gains your choice of vigilance, lifelink, or indestructible until end of turn.
            [−6]: Exile target nonland permanent.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Human")), SubtypeRef.Literal(Subtype("Soldier")))),
            Mechanic.SetPowerToughness(n(4), n(4)),
            has(Keyword.Indestructible),
            condition = Condition.YourTurn,
        ),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))), condition = Condition.YourTurn),
        loyalty(1,
            Step.Choose(Choice.Option(listOf("vigilance", "lifelink", "indestructible")), bind = "keyword", chooser = Players.You),
            ifThen(Condition.Chose("keyword", "vigilance"), gains(target("creature"), Keyword.Vigilance), otherwise = listOf(
                ifThen(Condition.Chose("keyword", "lifelink"), gains(target("creature"), Keyword.Lifelink), otherwise = listOf(
                    gains(target("creature"), Keyword.Indestructible))))),
            targets = listOf(slot("creature", and(another, creature, you), min = 0)),
        ),
        loyalty(-6, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 608.2c: what the exile moved is counted after it; CR 120.3: damage equal to the cards they own in exile, and life equal to the damage dealt
    @Test
    fun velkaOrzhovPretender() = card(
        name = "Velka, Orzhov Pretender",
        cost = "{1}{W}{B}",
        type = "Legendary Planeswalker — Kaya",
        loyalty = "3",
        text = """
            [+1]: Exile up to two target cards from a single graveyard. You gain 2 life if at least one creature card was exiled this way.
            [−1]: Exile target nonland permanent with mana value 1 or less.
            [−5]: Velka deals damage to target player equal to the number of cards that player owns in exile and you gain that much life.
        """,
    ) parsesTo abilities(
        loyalty(1,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("exiled"), creature)), Comparison.AtLeast, n(1)), gainLife(2)),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2).copy(sameOwner = true)),
        ),
        loyalty(-1, move(target("permanent"), Zone.Exile),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, manaValue(Comparison.AtMost, 1))))),
        loyalty(-5,
            doing(Effect.DealDamage(Recipients(players = Players.Target("player")),
                Value.Count(Objects.Each(and(inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))))), Objects.Self, bind = "damage")),
            doing(Effect.GainLife(Players.You, Value.Bound("damage"))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 611.2b: until your next turn; CR 613.4b: power and toughness each its mana value; CR 400.11b: a card from outside the game
    @Test
    fun vornTheGrandArtificer() = card(
        name = "Vorn, the Grand Artificer",
        cost = "{4}",
        type = "Legendary Planeswalker — Karn",
        loyalty = "5",
        text = """
            Activated abilities of artifacts your opponents control can't be activated.
            [+1]: Until your next turn, up to one target noncreature artifact becomes an artifact creature with power and toughness each equal to its mana value.
            [−2]: You may reveal an artifact card you own from outside the game or choose a face-up artifact card you own in exile. Put that card into your hand.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantActivate(ObjectFilter.AbilityOf(source = or(and(artifact, opponents))))),
            affects = arcanum.api.Affected.Players(Players.Each(PlayerFilter.Any))),
        loyalty(1,
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
                Mechanic.SetPowerToughness(Value.Of(NumericProperty.ManaValue, target("artifact"), Combine.Single), Value.Of(NumericProperty.ManaValue, target("artifact"), Combine.Single)),
            ), arcanum.api.Affected.Objects(target("artifact")), Duration.UntilYourNextTurn)),
            targets = listOf(slot("artifact", and(not(creature), artifact), min = 0)),
        ),
        loyalty(-2,
            Step.Choose(Choice.Objects(artifact, n(0), n(1), from = Objects.Each(or(
                and(inZone(Zone.Outside), ObjectFilter.OwnedBy(PlayerFilter.You)),
                and(inZone(Zone.Exile), not(ObjectFilter.HasStatus(Status.FaceDown)), ObjectFilter.OwnedBy(PlayerFilter.You))))), bind = "chosen", chooser = Players.You),
            ifThen(Condition.ObjectMatches(Objects.Bound("chosen"), inZone(Zone.Outside)), doing(Effect.Reveal(Objects.Bound("chosen")))),
            move(Objects.Bound("chosen"), Zone.Hand),
        ),
    )

    // CR 707.9a: the copy has the quoted ability as an exception; CR 107.3a, 613.4b: base power and toughness X/X for good
    @Test
    fun morphomass() = card(
        name = "Morphomass",
        cost = "{3}{U}",
        type = "Creature — Shapeshifter",
        pt = "0/0",
        text = "You may have this creature enter as a copy of any creature on the battlefield, except it has \"{X}: This creature has base power and toughness X/X.\"",
    ) parsesTo abilities(
        static(Mechanic.Replacement(entersSelf.let { (it as arcanum.api.Trigger.On).events.single() }, modify = listOf(Modification.EntersAsCopy(creature, listOf(
            CopyException.AddAbilities(listOf(withX(activated(manaCost("{X}"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.SetPowerToughness(Value.Announced("X"), Value.Announced("X"))),
                    arcanum.api.Affected.Objects(Objects.Self), Duration.Indefinitely)))))),
        ), optional = true)))),
    )

    // CR 500.8: an additional combat phase and main phase after this main phase; CR 702.7, 702.20
    @Test
    fun riposteResurge() = cardFaces(
        Layout.Split,
        card(name = "Riposte", cost = "{R/W}{R/W}", type = "Instant", text = "Riposte deals 5 damage to target attacking or blocking creature.") to abilities(
            spell(damage(5, toObjects(target("creature"))),
                targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature))))),
        card(name = "Resurge", cost = "{3}{R}{W}", type = "Sorcery", text = "Creatures you control gain first strike and vigilance until end of turn. After this main phase, there is an additional combat phase followed by an additional main phase.") to abilities(
            spell(gains(Objects.Each(creatureYouControl), Keyword.FirstStrike, Keyword.Vigilance), doing(Effect.ExtraCombat(followedByMain = true)))),
    )
}
