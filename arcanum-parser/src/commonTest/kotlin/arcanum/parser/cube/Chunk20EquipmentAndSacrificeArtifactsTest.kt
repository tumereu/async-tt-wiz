package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 20: Equipment (bonuses to the equipped creature, CR 301.5, with triggers on it dying or the Equipment coming
 * off), a Vehicle, one-shot artifacts that sacrifice themselves (spellbombs, baubles that draw at the next upkeep) and
 * sacrifice outlets whose effect reads the sacrificed creature.
 */
class Chunk20EquipmentAndSacrificeArtifactsTest {
    // ---- Shared shapes

    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private fun sacrificeA(filter: ObjectFilter, preBind: String? = null) = CostPart.Sacrifice(CostObjects.Choose(filter), preBind = preBind)
    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private fun draw(n: Int) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val creature = ObjectFilter.Type(CardType.Creature)

    private fun activated(vararg cost: CostPart, targets: List<TargetSlot> = emptyList(), steps: List<Step>) = Ability.Activated(
        cost = Cost(cost.toList()),
        instructions = Instructions(targets = targets, steps = steps),
    )

    /** "Equipped creature gets +N/+M." (CR 301.5) */
    private fun equippedGets(power: Value, toughness: Value) = Ability.Static(
        mechanics = listOf(Mechanic.ModifyPowerToughness(power, toughness)),
        affects = Affected.Objects(Objects.AttachedTo()),
    )

    private fun equippedGets(power: Int, toughness: Int) = equippedGets(Value.Fixed(power), Value.Fixed(toughness))
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))

    private val anyTarget = TargetSlot(
        name = "any",
        what = TargetKind.Either(
            objects = ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Type(CardType.Planeswalker),
                ObjectFilter.Type(CardType.Battle),
            )),
            players = PlayerFilter.Any,
        ),
    )
    private val targetPlayer = TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any))

    /** "[draw a card] at the beginning of the next turn's upkeep": a delayed trigger (CR 603.7). */
    private fun atNextUpkeep(vararg steps: Step) = Step.Do(Effect.CreateDelayedTrigger(
        trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep)),
        instructions = Instructions(steps = steps.toList()),
    ))

    private fun token(subtype: String, power: Int, toughness: Int, colors: List<Color>, artifact: Boolean, vararg keywords: Keyword) =
        TokenSpec.Defined(FaceDefinition(
            name = "$subtype Token",
            colorIndicator = colors,
            types = listOfNotNull(CardType.Artifact.takeIf { artifact }, CardType.Creature),
            subtypes = listOf(Subtype(subtype)),
            power = PrintedNumber.Fixed(power),
            toughness = PrintedNumber.Fixed(toughness),
            abilities = keywords.toList(),
        ))

    private fun createFood(n: Int) = Step.Do(Effect.CreateToken(TokenSpec.Predefined("Food"), count = Value.Fixed(n)))

    // ---- Equipment

    @Test
    fun skullBracer() = card(
        name = "Skull Bracer",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 for each artifact you control.
            {B}{B}: Attach this Equipment to target creature you control.
            Equip {1}
        """,
    ) parsesTo abilities(
        equippedGets(
            power = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), youControl)))),
            toughness = Value.Fixed(0),
        ),
        // CR 701.3a: an attach ability of its own; unlike equip (CR 702.6a) it has no sorcery timing
        activated(
            mana("{B}{B}"),
            targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(ObjectFilter.And(listOf(creature, youControl))))),
            steps = listOf(Step.Do(Effect.Attach(Objects.Self, to = Recipients(objects = Objects.Target("creature"))))),
        ),
        equip("{1}"),
    )

    @Test
    fun fusedBattleplate() = card(
        name = "Fused Battleplate",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+2.
            Whenever this Equipment becomes unattached from a permanent, sacrifice that permanent.
            Equip {0} ({0}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        equippedGets(3, 2),
        // CR 701.3d: "that permanent" is the object it was attached to
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Unattach(
                objects = ObjectFilter.Is(Objects.Self),
                from = ObjectFilter.Category(ObjectCategory.Permanent),
            )),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Sacrifice(Objects.EventObject(EventRole.From)))),
            ),
        ),
        equip("{0}"),
    )

    @Test
    fun thornpodHusk() = card(
        name = "Thornpod Husk",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +1/+1 for each artifact and/or enchantment you control.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        Value.Count(Objects.Each(ObjectFilter.And(listOf(
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))),
            youControl,
        )))).let { each -> equippedGets(power = each, toughness = each) },
        equip("{2}"),
    )

    @Test
    fun bonevise() = card(
        name = "Bonevise",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/-1.
            Whenever equipped creature dies, draw two cards.
            Equip {1}
        """,
    ) parsesTo abilities(
        equippedGets(1, -1),
        // CR 603.10a: a dies trigger looks back in time, when the creature was still equipped
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.Is(Objects.AttachedTo()),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(
                steps = listOf(draw(2)),
            ),
        ),
        equip("{1}"),
    )

    @Test
    fun bladeOfTheHumble() = card(
        name = "Blade of the Humble",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+2.
            Equip {2}
            Whenever a 1/1 creature you control enters, you may return this card from your graveyard to the battlefield, then attach it to that creature.
        """,
    ) parsesTo abilities(
        equippedGets(1, 2),
        equip("{2}"),
        // CR 113.6m: its effect moves it out of the graveyard, so it works only there (no zones written)
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.And(listOf(
                    creature,
                    ObjectFilter.Power(Comparison.Equal, Value.Fixed(1)),
                    ObjectFilter.Toughness(Comparison.Equal, Value.Fixed(1)),
                    youControl,
                )),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.May(listOf(
                        Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield), postBind = "returned")),
                        Step.Do(Effect.Attach(Objects.Bound("returned"), to = Recipients(objects = Objects.EventObject(EventRole.After)))),
                    )),
                ),
            ),
        ),
    )

    // ---- Vehicle

    @Test
    fun runnersSkiff() = card(
        name = "Runner's Skiff",
        cost = "{2}",
        type = "Artifact — Vehicle",
        pt = "3/3",
        text = """
            Flying
            Whenever this Vehicle attacks or blocks, you may draw a card. If you do, discard a card.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        // One ability with two trigger events; "you may … If you do" is a Pay decided on resolution (CR 603.5)
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.Attack(ObjectFilter.Is(Objects.Self)),
                EventPattern.Block(ObjectFilter.Is(Objects.Self)),
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(steps = listOf(draw(1)), bind = "paid"),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1)))),
                    ),
                ),
            ),
        ),
        Keyword.Crew(1),
    )

    // ---- Spellbombs, baubles and tops

    @Test
    fun tidalSpellcharge() = card(
        name = "Tidal Spellcharge",
        cost = "{1}",
        type = "Artifact",
        text = """
            {U}, Sacrifice this artifact: Return target creature to its owner's hand.
            {1}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        activated(
            mana("{U}"), sacrificeSelf,
            targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(creature))),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Hand)))),
        ),
        activated(mana("{1}"), sacrificeSelf, steps = listOf(draw(1))),
    )

    @Test
    fun cinderSpellcharge() = card(
        name = "Cinder Spellcharge",
        cost = "{1}",
        type = "Artifact",
        text = """
            {R}, Sacrifice this artifact: It deals 2 damage to any target.
            {1}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        // "It" is the sacrificed source, its last known information (CR 113.7a)
        activated(
            mana("{R}"), sacrificeSelf,
            targets = listOf(anyTarget),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(2)))),
        ),
        activated(mana("{1}"), sacrificeSelf, steps = listOf(draw(1))),
    )

    @Test
    fun mendersTrinket() = card(
        name = "Mender's Trinket",
        cost = "{1}",
        type = "Artifact",
        text = "{T}, Sacrifice this artifact: Put up to one target card from your graveyard on the bottom of your library. Draw a card.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, sacrificeSelf,
            targets = listOf(TargetSlot(
                name = "card",
                what = TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Category(ObjectCategory.Card),
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))),
                min = Value.Fixed(0),
                max = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Library, libraryPosition = -1))),
                draw(1),
            ),
        ),
    )

    @Test
    fun seersTrinket() = card(
        name = "Seer's Trinket",
        cost = "{0}",
        type = "Artifact",
        text = "{T}, Sacrifice this artifact: Look at the top card of target player's library. Draw a card at the beginning of the next turn's upkeep.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, sacrificeSelf,
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.Target("player"), count = Value.Fixed(1)))),
                atNextUpkeep(draw(1)),
            ),
        ),
    )

    @Test
    fun spysTrinket() = card(
        name = "Spy's Trinket",
        cost = "{0}",
        type = "Artifact",
        text = "{T}, Sacrifice this artifact: Look at a card at random in target player's hand. You draw a card at the beginning of the next turn's upkeep.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, sacrificeSelf,
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.Random(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))),
                )))))),
                atNextUpkeep(draw(1)),
            ),
        ),
    )

    @Test
    fun oraclesSpinningTop() = card(
        name = "Oracle's Spinning Top",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}: Look at the top three cards of your library, then put them back in any order.
            {T}: Draw a card, then put this artifact on top of its owner's library.
        """,
    ) parsesTo abilities(
        activated(
            mana("{1}"),
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, count = Value.Fixed(3)), bind = "seen")),
                Step.Do(Effect.Reorder(Objects.Bound("seen"))),
            ),
        ),
        activated(
            CostPart.TapSelf,
            steps = listOf(
                draw(1),
                Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Library, libraryPosition = 0))),
            ),
        ),
    )

    // ---- Sacrifice outlets

    @Test
    fun shrineOfOblivion() = card(
        name = "Shrine of Oblivion",
        cost = "{2}",
        type = "Artifact",
        text = "Sacrifice a creature: Target player mills cards equal to the sacrificed creature's power.",
    ) parsesTo abilities(
        // CR 608.2h: the sacrificed creature's power is its last known information
        activated(
            sacrificeA(creature, preBind = "sacrificed"),
            targets = listOf(targetPlayer),
            steps = listOf(Step.Do(Effect.Mill(
                Players.Target("player"),
                count = Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Single),
            ))),
        ),
    )

    @Test
    fun detonationRig() = card(
        name = "Detonation Rig",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}, Sacrifice a creature: This artifact deals 1 damage to any target.
            Whenever a creature enters, you may untap this artifact.
        """,
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, sacrificeA(creature),
            targets = listOf(anyTarget),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(1)))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creature, to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.May(listOf(Step.Do(Effect.Untap(Objects.Self))))),
            ),
        ),
    )

    @Test
    fun lifeseedOrb() = card(
        name = "Lifeseed Orb",
        cost = "{0}",
        type = "Artifact",
        text = "Sacrifice a land: You gain 2 life.",
    ) parsesTo abilities(
        activated(
            sacrificeA(ObjectFilter.Type(CardType.Land)),
            steps = listOf(Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(2)))),
        ),
    )

    @Test
    fun hagsKiln() = card(
        name = "Hag's Kiln",
        cost = "{1}",
        type = "Artifact",
        text = """
            {T}, Sacrifice a creature: Create a Food token. If the sacrificed creature's toughness was 4 or greater, create two Food tokens instead. (They're artifacts with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        // A conditional "instead" in the ability's own text is If/otherwise (CR 608.2c); CR 111.10b: Food
        activated(
            CostPart.TapSelf, sacrificeA(creature, preBind = "sacrificed"),
            steps = listOf(
                Step.If(
                    condition = Condition.ObjectMatches(
                        Objects.Bound("sacrificed"),
                        ObjectFilter.Toughness(Comparison.AtLeast, Value.Fixed(4)),
                    ),
                    then = listOf(createFood(2)),
                    otherwise = listOf(createFood(1)),
                ),
            ),
        ),
    )

    @Test
    fun wingwrightForge() = card(
        name = "Wingwright Forge",
        cost = "{W/B}{U}",
        type = "Artifact",
        text = "{1}, Sacrifice a nontoken artifact: Create a 1/1 blue Thopter artifact creature token with flying. You gain 1 life.",
    ) parsesTo abilities(
        activated(
            mana("{1}"),
            sacrificeA(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                ObjectFilter.Type(CardType.Artifact),
            ))),
            steps = listOf(
                Step.Do(Effect.CreateToken(token("Thopter", 1, 1, listOf(Color.Blue), artifact = true, Keyword.Flying))),
                Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(1))),
            ),
        ),
    )

    @Test
    fun theGravecooksLedger() = card(
        name = "The Gravecook's Ledger",
        cost = "{1}",
        type = "Artifact — Book",
        text = """
            {T}, Discard a card: Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {4}, {T}, Sacrifice this artifact: Return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))),
            steps = listOf(createFood(1)),
        ),
        activated(
            mana("{4}"), CostPart.TapSelf, sacrificeSelf,
            targets = listOf(TargetSlot(
                name = "card",
                what = TargetKind.Objects(ObjectFilter.And(listOf(
                    creature,
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))),
            )),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
        ),
    )
}
