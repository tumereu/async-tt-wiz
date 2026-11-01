package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 9: modal spells ("Choose one —" with • modes, each mode with its own targets, CR 700.2) and spells that move
 * cards around: target player draws or discards, looting, digging through the library, shuffling hands and graveyards
 * away, flickering, and a token-making spell with flashback.
 */
class Chunk09ModesAndCardFlowTest {
    // ---- Shared shapes

    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(
        name = name,
        what = TargetKind.Objects(filter),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val targetPlayer = TargetSlot(
        name = "player",
        what = TargetKind.Players(PlayerFilter.Any),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
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
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )

    private fun modal(vararg modes: Instructions, choose: Int = 1, repeatable: Boolean = false) = Ability.Spell(Instructions(
        modes = Modes(
            options = modes.map { Mode(it) },
            min = choose,
            max = choose,
            repeatable = repeatable,
            chooser = Players.You,
        ),
    ))

    /** "Destroy target artifact." as a mode. */
    private val destroyArtifact = Instructions(
        targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("artifact")))),
    )

    private fun damageToTargetCreature(amount: Int) = Instructions(
        targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
        steps = listOf(Step.Do(Effect.DealDamage(
            Recipients(objects = Objects.Target("creature")),
            amount = Value.Fixed(amount),
            source = Objects.Self,
        ))),
    )

    private fun damageToAnyTarget(amount: Int) = Instructions(
        targets = listOf(anyTarget),
        steps = listOf(Step.Do(Effect.DealDamage(
            Recipients(target = "any"),
            amount = Value.Fixed(amount),
            source = Objects.Self,
        ))),
    )

    private fun draw(players: Players, n: Int) = Step.Do(Effect.Draw(players, count = Value.Fixed(n)))
    private fun discard(players: Players, n: Int) = Step.Do(Effect.Discard(
        players,
        count = Value.Fixed(n),
        filter = ObjectFilter.Any,
    ))

    /** "Each player shuffles their hand and graveyard into their library, then draws seven cards." */
    private val wheel = listOf(
        Step.Do(Effect.Move(
            Objects.Each(ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard)))),
            to = Destination(Zone.Library),
        )),
        Step.Do(Effect.Shuffle(Players.Each(PlayerFilter.Any))),
        draw(Players.Each(PlayerFilter.Any), 7),
    )

    // ---- Modal spells

    @Test
    fun grindstoneRasp() = card(
        name = "Grindstone Rasp",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Grindstone Rasp deals 3 damage to target creature.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        modal(
            damageToTargetCreature(3),
            destroyArtifact,
        ),
    )

    @Test
    fun stormforgeCharm() = card(
        name = "Stormforge Charm",
        cost = "{U}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target noncreature spell unless its controller pays {2}.
            • Stormforge Charm deals 2 damage to target creature.
            • Draw two cards, then discard two cards.
        """,
    ) parsesTo abilities(
        modal(
            Instructions(
                targets = listOf(slot("spell", ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)),
                    ObjectFilter.Category(ObjectCategory.Spell),
                )))),
                steps = listOf(
                    Step.Pay(
                        cost = manaCost("{2}"),
                        payer = Players.ControllerOf(Objects.Target("spell")),
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(Condition.Did("paid")),
                        then = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))),
                    ),
                ),
            ),
            damageToTargetCreature(2),
            Instructions(steps = listOf(draw(Players.You, 2), discard(Players.You, 2))),
        ),
    )

    @Test
    fun warlordsDecree() = card(
        name = "Warlord's Decree",
        cost = "{1}{B}{R}",
        type = "Instant",
        text = """
            Choose two —
            • Return target creature card from your graveyard to your hand.
            • Target player discards a card.
            • Destroy target artifact.
            • Warlord's Decree deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        modal(
            Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(
                    ObjectFilter.Type(CardType.Creature),
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                )))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
            ),
            Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(discard(Players.Target("player"), 1)),
            ),
            destroyArtifact,
            damageToAnyTarget(2),
            choose = 2,
        ),
    )

    @Test
    fun spellforgeCommand() = card(
        name = "Spellforge Command",
        cost = "{1}{U}{R}",
        type = "Instant",
        text = """
            Choose two —
            • Spellforge Command deals 2 damage to any target.
            • Target player draws two cards, then discards two cards.
            • Target player creates a Treasure token.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        modal(
            damageToAnyTarget(2),
            Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(draw(Players.Target("player"), 2), discard(Players.Target("player"), 2)),
            ),
            Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(Step.Do(Effect.CreateToken(
                    TokenSpec.Predefined("Treasure"),
                    controller = Players.Target("player"),
                    count = Value.Fixed(1),
                ))),
            ),
            destroyArtifact,
            choose = 2,
        ),
    )

    @Test
    fun blazingConfluence() = card(
        name = "Blazing Confluence",
        cost = "{2}{R}{R}",
        type = "Sorcery",
        text = """
            Choose three. You may choose the same mode more than once.
            • Blazing Confluence deals 1 damage to each creature.
            • Blazing Confluence deals 2 damage to each opponent.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        modal(
            Instructions(steps = listOf(
                Step.Do(Effect.DealDamage(
                    Recipients(objects = Objects.Each(ObjectFilter.Type(CardType.Creature))),
                    amount = Value.Fixed(1),
                    source = Objects.Self,
                )),
            )),
            Instructions(steps = listOf(
                Step.Do(Effect.DealDamage(
                    Recipients(players = Players.Each(PlayerFilter.Opponent)),
                    amount = Value.Fixed(2),
                    source = Objects.Self,
                )),
            )),
            destroyArtifact,
            choose = 3,
            repeatable = true,
        ),
    )

    // ---- Drawing and looting

    @Test
    fun thoroughStudy() = card(
        name = "Thorough Study",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Target player draws two cards.
            Flashback—{1}{U}, Pay 3 life. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(draw(Players.Target("player"), 2)),
        )),
        Keyword.Flashback(Cost(listOf(
            CostPart.Mana(ManaCost.parse("{1}{U}")!!),
            CostPart.PayLife(Value.Fixed(3)),
        ))),
    )

    @Test
    fun faithlessRummage() = card(
        name = "Faithless Rummage",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Draw two cards, then discard two cards.
            Flashback {2}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(draw(Players.You, 2), discard(Players.You, 2)))),
        Keyword.Flashback(manaCost("{2}{R}")),
    )

    @Test
    fun hoardVoyage() = card(
        name = "Hoard Voyage",
        cost = "{7}{U}",
        type = "Sorcery",
        text = """
            Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)
            Draw three cards.
        """,
    ) parsesTo abilities(
        Keyword.Delve,
        Ability.Spell(Instructions(steps = listOf(draw(Players.You, 3)))),
    )

    @Test
    fun delveThroughAges() = card(
        name = "Delve Through Ages",
        cost = "{6}{U}{U}",
        type = "Instant",
        text = """
            Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)
            Look at the top seven cards of your library. Put two of them into your hand and the rest on the bottom of your library in any order.
        """,
    ) parsesTo abilities(
        Keyword.Delve,
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.LookAt(
                Objects.LibraryCards(Players.You, Value.Fixed(7), fromTop = true),
                bind = "seen",
                players = Players.You,
            )),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.Any, min = Value.Fixed(2), max = Value.Fixed(2), from = Objects.Bound("seen")),
                bind = "kept",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("kept"), to = Destination(Zone.Hand))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                to = Destination(Zone.Library, libraryPosition = -1),
            )),
        ))),
    )

    @Test
    fun expressiveVariation() = card(
        name = "Expressive Variation",
        cost = "{U}{R}",
        type = "Sorcery",
        text = "Look at the top three cards of your library. Put one of them into your hand, put one of them on the bottom of your library, and exile one of them. You may play the exiled card this turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.LookAt(
                Objects.LibraryCards(Players.You, Value.Fixed(3), fromTop = true),
                bind = "seen",
                players = Players.You,
            )),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.Any, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Bound("seen")),
                bind = "toHand",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("toHand"), to = Destination(Zone.Hand))),
            Step.Choose(
                choice = Choice.Objects(
                    ObjectFilter.Any,
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                    from = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand")),
                ),
                bind = "toBottom",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("toBottom"), to = Destination(Zone.Library, libraryPosition = -1))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand")), Objects.Bound("toBottom")),
                to = Destination(Zone.Exile),
                postBind = "exiled",
            )),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Bound("exiled")),
                duration = Duration.EndOfTurn,
            )),
        ))),
    )

    // ---- Wheels

    @Test
    fun echoOfAges() = card(
        name = "Echo of Ages",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Each player shuffles their hand and graveyard into their library, then draws seven cards.
            Flashback {2}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = wheel)),
        Keyword.Flashback(manaCost("{2}{U}")),
    )

    @Test
    fun timeWhorl() = card(
        name = "Time Whorl",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Exile Time Whorl. Each player shuffles their hand and graveyard into their library, then draws seven cards. You untap up to six lands.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Exile)))) + wheel + listOf(
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.Type(CardType.Land), min = Value.Fixed(0), max = Value.Fixed(6)),
                bind = "untapped",
                chooser = Players.You,
            ),
            Step.Do(Effect.Untap(Objects.Bound("untapped"))),
        ))),
    )

    // ---- Flicker and tokens

    @Test
    fun fleetingRecall() = card(
        name = "Fleeting Recall",
        cost = "{W}",
        type = "Instant",
        text = """
            Exile target creature you control, then return it to the battlefield under its owner's control.
            Rebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.ControlledBy(PlayerFilter.You),
            )))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(
                    Objects.Bound("exiled"),
                    to = Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                )),
            ),
        )),
        Keyword.Rebound,
    )

    @Test
    fun lingeringShades() = card(
        name = "Lingering Shades",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Create two 1/1 white Spirit creature tokens with flying.
            Flashback {1}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateToken(
                token = TokenSpec.Defined(FaceDefinition(
                    name = "Spirit Token",
                    colorIndicator = listOf(Color.White),
                    types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Spirit")),
                    power = PrintedNumber.Fixed(1),
                    toughness = PrintedNumber.Fixed(1),
                    abilities = listOf(Keyword.Flying),
                )),
                count = Value.Fixed(2),
                controller = Players.You,
            )),
        ))),
        Keyword.Flashback(manaCost("{1}{B}")),
    )
}
