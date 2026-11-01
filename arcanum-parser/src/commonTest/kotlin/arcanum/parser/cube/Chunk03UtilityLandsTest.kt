package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 3: utility lands. Triomes enter tapped and have cycling; their mana comes from their three basic land types
 * (CR 305.6). Others: lands that make mana for each creature or artifact, mana for artifact spells only, a looting land,
 * a token-making land, and a land with a channel ability (an ability word, CR 207.2c).
 */
class Chunk03UtilityLandsTest {
    // ---- Shared shapes

    /** "This land enters tapped." */
    private val entersTapped = Ability.Static(listOf(
        Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped),
        ),
    ))

    /** "{T}: Add {C}." */
    private val tapForColorless = Ability.Activated(
        cost = Cost(listOf(CostPart.TapSelf)),
        instructions = Instructions(
            steps = listOf(
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)))),
            ),
        ),
    )

    /** "{T}: Add {[mana]} for each [what]." */
    private fun tapForEach(mana: String, what: ObjectFilter) = Ability.Activated(
        cost = Cost(listOf(CostPart.TapSelf)),
        instructions = Instructions(
            steps = listOf(
                Step.Do(Effect.AddMana(ManaProduction(
                    ManaKind.Fixed(ManaCost.parse(mana)!!, times = Value.Count(Objects.Each(what))),
                ))),
            ),
        ),
    )

    private fun yourOwn(type: CardType) = ObjectFilter.And(listOf(
        ObjectFilter.Type(type),
        ObjectFilter.ControlledBy(PlayerFilter.You),
    ))

    // ---- Triomes: "This land enters tapped." and cycling {3}

    @Test
    fun hallowedMireTriad() = card(
        name = "Hallowed Mire Triad",
        cost = null,
        type = "Land — Plains Swamp Forest",
        text = """
            ({T}: Add {W}, {B}, or {G}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun revelersArbor() = card(
        name = "Revelers' Arbor",
        cost = null,
        type = "Land — Mountain Forest Plains",
        text = """
            ({T}: Add {R}, {G}, or {W}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun stormrootTriad() = card(
        name = "Stormroot Triad",
        cost = null,
        type = "Land — Forest Island Mountain",
        text = """
            ({T}: Add {G}, {U}, or {R}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun spymastersSpire() = card(
        name = "Spymaster's Spire",
        cost = null,
        type = "Land — Plains Island Swamp",
        text = """
            ({T}: Add {W}, {U}, or {B}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun skyforgeTriad() = card(
        name = "Skyforge Triad",
        cost = null,
        type = "Land — Island Mountain Plains",
        text = """
            ({T}: Add {U}, {R}, or {W}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun ashlightTriad() = card(
        name = "Ashlight Triad",
        cost = null,
        type = "Land — Mountain Plains Swamp",
        text = """
            ({T}: Add {R}, {W}, or {B}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun gardenersLoft() = card(
        name = "Gardener's Loft",
        cost = null,
        type = "Land — Forest Plains Island",
        text = """
            ({T}: Add {G}, {W}, or {U}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun velvetDen() = card(
        name = "Velvet Den",
        cost = null,
        type = "Land — Island Swamp Mountain",
        text = """
            ({T}: Add {U}, {B}, or {R}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun bogshadeTriad() = card(
        name = "Bogshade Triad",
        cost = null,
        type = "Land — Swamp Forest Island",
        text = """
            ({T}: Add {B}, {G}, or {U}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    @Test
    fun scalelordsArena() = card(
        name = "Scalelord's Arena",
        cost = null,
        type = "Land — Swamp Mountain Forest",
        text = """
            ({T}: Add {B}, {R}, or {G}.)
            This land enters tapped.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        Keyword.Cycling(manaCost("{3}")),
    )

    // ---- Lands that make a lot of mana

    @Test
    fun mothersBower() = card(
        name = "Mother's Bower",
        cost = null,
        type = "Legendary Land",
        text = "{T}: Add {G} for each creature you control.",
    ) parsesTo abilities(
        tapForEach("{G}", what = yourOwn(CardType.Creature)),
    )

    @Test
    fun arcanistCollegium() = card(
        name = "Arcanist Collegium",
        cost = null,
        type = "Legendary Land",
        text = "{T}: Add {U} for each artifact you control.",
    ) parsesTo abilities(
        tapForEach("{U}", what = yourOwn(CardType.Artifact)),
    )

    @Test
    fun tinkersAtelier() = card(
        name = "Tinker's Atelier",
        cost = null,
        type = "Land",
        text = "{T}: Add {C}{C}{C}. Spend this mana only to cast artifact spells.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.AddMana(ManaProduction(
                        kind = ManaKind.Fixed(ManaCost.parse("{C}{C}{C}")!!),
                        restriction = ObjectFilter.And(listOf(
                            ObjectFilter.Category(ObjectCategory.Spell),
                            ObjectFilter.Type(CardType.Artifact),
                        )),
                    ))),
                ),
            ),
        ),
    )

    // ---- Other utility lands

    @Test
    fun soukOfTheSandKings() = card(
        name = "Souk of the Sand Kings",
        cost = null,
        type = "Land",
        text = "{T}: Draw two cards, then discard three cards.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Draw(Players.You, count = Value.Fixed(2))),
                    Step.Do(Effect.Discard(Players.You, count = Value.Fixed(3))),
                ),
            ),
        ),
    )

    @Test
    fun barrowOfTheRestless() = card(
        name = "Barrow of the Restless",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {C}.
            Whenever this land or another land you control enters, if you control seven or more lands with different names, create a 2/2 black Zombie creature token.
        """,
    ) parsesTo abilities(
        entersTapped,
        tapForColorless,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.Or(listOf(
                    ObjectFilter.Is(Objects.Self),
                    ObjectFilter.And(listOf(
                        ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                        ObjectFilter.Type(CardType.Land),
                        ObjectFilter.ControlledBy(PlayerFilter.You),
                    )),
                )),
                to = Zone.Battlefield,
            )),
            interveningIf = Condition.Compare(
                left = Value.CountDistinct(Objects.Each(yourOwn(CardType.Land)), SharedQuality.Name),
                comparison = Comparison.AtLeast,
                right = Value.Fixed(7),
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                        name = "Zombie Token",
                        colorIndicator = listOf(Color.Black),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Zombie")),
                        power = PrintedNumber.Fixed(2),
                        toughness = PrintedNumber.Fixed(2),
                    )))),
                ),
            ),
        ),
    )

    @Test
    fun okuraiWhoAbides() = card(
        name = "Okurai, Who Abides",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {G}.
            Channel — {1}{G}, Discard this card: Destroy target artifact, enchantment, or nonbasic land an opponent controls. That player may search their library for a land card with a basic land type, put it onto the battlefield, then shuffle. This ability costs {1} less to activate for each legendary creature you control.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)))),
                ),
            ),
        ),
        Ability.Activated(
            abilityWord = "Channel",
            cost = Cost(listOf(
                CostPart.Mana(ManaCost.parse("{1}{G}")!!),
                CostPart.Discard(CostObjects.Self),
            )),
            rules = ActivationRules(
                costChange = Value.Times(
                    Value.Count(Objects.Each(ObjectFilter.And(listOf(
                        ObjectFilter.Super(Supertype.Legendary),
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.ControlledBy(PlayerFilter.You),
                    )))),
                    factor = -1,
                ),
            ),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(ObjectFilter.And(listOf(
                            ObjectFilter.Or(listOf(
                                ObjectFilter.Type(CardType.Artifact),
                                ObjectFilter.Type(CardType.Enchantment),
                                ObjectFilter.And(listOf(
                                    ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic)),
                                    ObjectFilter.Type(CardType.Land),
                                )),
                            )),
                            ObjectFilter.ControlledBy(PlayerFilter.Opponent),
                        ))),
                    ),
                ),
                steps = listOf(
                    Step.Do(Effect.Destroy(Objects.Target("permanent"))),
                    Step.May(
                        player = Players.ControllerOf(Objects.Target("permanent")),
                        steps = listOf(
                            Step.Do(Effect.Search(
                                library = Players.ControllerOf(Objects.Target("permanent")),
                                filter = ObjectFilter.And(listOf(
                                    ObjectFilter.Type(CardType.Land),
                                    ObjectFilter.Or(listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { ObjectFilter.Sub(Subtype(it)) }),
                                )),
                                bind = "found",
                            )),
                            Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                            Step.Do(Effect.Shuffle(Players.ControllerOf(Objects.Target("permanent")))),
                        ),
                    ),
                ),
            ),
        ),
    )
}
