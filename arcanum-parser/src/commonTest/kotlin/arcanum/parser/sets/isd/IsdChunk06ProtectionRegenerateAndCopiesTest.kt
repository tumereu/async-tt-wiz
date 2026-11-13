package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 06: protection from creature types (CR 702.16), regeneration (CR 701.19), keyword conditions and losing keywords
 * (CR 613.1f), copies as they enter (CR 707.9), "can't be cast" by name, prevention with a rider (CR 615.5) and counted tokens.
 */
class IsdChunk06ProtectionRegenerateAndCopiesTest {
    private val zombieYouControl = and(sub("Zombie"), you)
    private fun regenerate(objects: Objects) = Step.Do(Effect.Action(KeywordAction.Regenerate(objects)))
    private val study = CounterKind.Named("study")

    // CR 702.16a, g: "protection from A, from B, and from C" is three protection abilities, each from a creature type
    @Test
    fun vigilInquisitor() = card(
        name = "Vigil Inquisitor",
        cost = "{W}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            First strike, vigilance
            Protection from Vampires, from Werewolves, and from Zombies
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Vigilance,
        Keyword.Protection(sub("Vampire")),
        Keyword.Protection(sub("Werewolf")),
        Keyword.Protection(sub("Zombie")),
    )

    // CR 702.3a (defender), 702.16a (protection from a creature type)
    @Test
    fun cryptThornbush() = card(
        name = "Crypt Thornbush",
        cost = "{1}{G}{G}",
        type = "Creature — Plant",
        pt = "3/4",
        text = "Defender, protection from Zombies",
    ) parsesTo abilities(
        Keyword.Defender,
        Keyword.Protection(sub("Zombie")),
    )

    // CR 613.4c, 613.1f: the bonus and the keyword; CR 701.19a: each Werewolf creature you control as the ability resolves
    @Test
    fun moonswellRite() = card(
        name = "Moonswell Rite",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Werewolf creatures you control get +1/+0 and have trample.
            Sacrifice this enchantment: Regenerate all Werewolf creatures you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Trample)),
            affects = Objects.Each(and(sub("Werewolf"), creature, you))),
        activated(cost(sacrificeSelf), regenerate(Objects.Each(and(sub("Werewolf"), creature, you)))),
    )

    // CR 712 (transforming double-faced card), 603.4 (intervening "if"), 701.19a (regenerate)
    @Test
    fun thornveilMystics() = cardFaces(Layout.Transform,
        card(
            name = "Thornveil Mystics",
            cost = "{2}{G}{G}",
            type = "Creature — Human Shaman Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "3/3",
        ) to abilities(werewolfFront),
        card(
            name = "Thornveil Primordials",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                {G}: Regenerate this creature.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "5/5",
            colorIndicator = "G",
        ) to abilities(
            activated(manaCost("{G}"), regenerate(Objects.Self)),
            werewolfBack,
        ),
    )

    // CR 701.24a (shuffle into library), 701.20a (reveal until), 110.2a: the owner puts the found card onto the battlefield under their control;
    // "If that player does": only if it was shuffled in (CR 608.2c)
    @Test
    fun glassmadWraith() = card(
        name = "Glassmad Wraith",
        cost = "{3}{U}{U}",
        type = "Creature — Spirit",
        pt = "5/1",
        text = """
            Flying
            {1}{U}: This creature's owner shuffles it into their library. If that player does, they reveal cards from the top of that library until a card named Glassmad Wraith is revealed. The player puts that card onto the battlefield and all other cards revealed this way into their graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{1}{U}"),
            move(Objects.Self, Zone.Library, postBind = "shuffled"),
            Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))),
            ifThen(Condition.NonEmpty("shuffled"),
                Step.Do(Effect.RevealUntil(ObjectFilter.Named("Glassmad Wraith"), Players.OwnerOf(Objects.Self), n(1), reveal = true,
                    foundBind = "found", restBind = "rest")),
                move(Objects.Bound("found"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Self)),
                move(Objects.Bound("rest"), Zone.Graveyard)),
        ),
    )

    // CR 611.3a: indestructible only while it has defender; CR 613.1f: losing defender and gaining flying until end of turn
    @Test
    fun belfryGargoyle() = card(
        name = "Belfry Gargoyle",
        cost = "{5}",
        type = "Artifact Creature — Gargoyle",
        pt = "4/4",
        text = """
            Defender
            This creature has indestructible as long as it has defender.
            {1}: Until end of turn, this creature loses defender and gains flying.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Mechanic.GainAbilities(listOf(Keyword.Indestructible)), condition = Condition.ObjectMatches(Objects.Self, kw(Keyword.Defender.kind))),
        activated(manaCost("{1}"), untilEndOfTurn(Objects.Self, Mechanic.LoseAbilities(Keyword.Defender.kind), Mechanic.GainAbilities(listOf(Keyword.Flying)))),
    )

    // CR 707.9a, b: the copy has the added ability; "the same name as this creature" is the name the copy has (CR 201.2)
    @Test
    fun sinisterDoppel() = card(
        name = "Sinister Doppel",
        cost = "{2}{U}{B}",
        type = "Creature — Shapeshifter",
        pt = "0/0",
        text = "You may have this creature enter as a copy of any creature on the battlefield, except it has \"{U}{B}, {T}: Destroy target creature with the same name as this creature.\"",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersAsCopy(creature, listOf(CopyException.AddAbilities(listOf(
                activated(cost(mana("{U}{B}"), tap), destroy(target("creature")),
                    targets = listOf(slot("creature", and(creature, ObjectFilter.SameNameAs(Objects.Self))))),
            ))), optional = true)))),
    )

    // CR 602.1 (costs), 118.3 (remove counters and sacrifice, both paid), 611.2e: they enter as black Zombies (layers 4 and 5)
    @Test
    fun tomeOfTheRestless() = card(
        name = "Tome of the Restless",
        cost = "{4}",
        type = "Legendary Artifact — Book",
        text = """
            {1}, {T}, Discard a card: Put a study counter on Tome of the Restless.
            {T}, Remove three study counters from Tome of the Restless and sacrifice it: Put all creature cards from all graveyards onto the battlefield under your control. They're black Zombies in addition to their other colors and types.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, discardACard), counters(Objects.Self, 1, study)),
        activated(cost(tap, CostPart.RemoveCounters(study, n(3), CostObjects.Self), sacrificeSelf),
            Step.Do(Effect.Move(Objects.Each(and(creature, inZone(Zone.Graveyard))), Destination(Zone.Battlefield, controller = Players.You,
                entersAs = listOf(Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black)), add = true),
                    Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Zombie")))))))),
        ),
    )

    // CR 614.12, 607.2d: the name chosen as it enters; CR 101.2: no player can cast spells with that name
    @Test
    fun neverwhen() = card(
        name = "Neverwhen",
        cost = "{1}{W}{W}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose a nonland card name.
            Spells with the chosen name can't be cast.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CardName(nonland), bind = "name", chooser = Players.You, remember = true),
        ))))),
        Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.RememberedName("name"))), affects = Affected.Players(Players.Each(PlayerFilter.Any))),
    )

    // CR 614.1c, 707.9: each creature entering under your control enters as a copy of this one (not optional)
    @Test
    fun spiritOfTheThicket() = card(
        name = "Spirit of the Thicket",
        cost = "{3}{G}{G}{G}",
        type = "Creature — Avatar",
        pt = "6/6",
        text = "Creatures you control enter as a copy of this creature.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(creature, you), to = Zone.Battlefield),
            modify = listOf(Modification.EntersAsCopy(ObjectFilter.Is(Objects.Self), optional = false)))),
    )

    // CR 614.1c (enters with counters, both counts added), 615.5 (prevention with an extra action)
    @Test
    fun breathlessThrong() = card(
        name = "Breathless Throng",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        pt = "0/0",
        text = """
            This creature enters with a +1/+1 counter on it for each other Zombie you control and each Zombie card in your graveyard.
            If this creature would be dealt damage, prevent that damage and remove a +1/+1 counter from it.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, Value.Sum(listOf(count(and(another, zombieYouControl)), count(inYourGraveyard(sub("Zombie"))))))))),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))),
            additional = Instructions(steps = listOf(Step.Do(Effect.RemoveCounters(Objects.Self, p1p1, n(1))))))),
    )

    // CR 107.1a: half the number, rounded down
    @Test
    fun ceaselessRanks() = card(
        name = "Ceaseless Ranks",
        cost = "{2}{B}{B}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, create X 2/2 black Zombie creature tokens, where X is half the number of Zombies you control, rounded down.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Do(Effect.CreateToken(zombie, Value.Half(count(zombieYouControl), roundUp = false), Players.You))),
    )

    // CR 508.4: created tapped and attacking, not declared as attackers
    @Test
    fun kennelBreakers() = card(
        name = "Kennel Breakers",
        cost = "{4}{G}",
        type = "Creature — Human Rogue",
        pt = "3/4",
        text = "Whenever this creature attacks, create a 2/2 green Wolf creature token that's tapped and attacking for each creature card in your graveyard.",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Do(Effect.CreateToken(wolf, count(inYourGraveyard(creature)), Players.You, tapped = true,
            attacking = AttackTarget.Chosen))),
    )
}
