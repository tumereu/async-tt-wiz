package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 09: effects first read here: a group that gets -1/-1 for each counter on each of them, a creature with the activated
 * abilities of the cards exiled with it, "you look at" and "put N of those cards", a list keeping its subject, casting from the top
 * of the library only what shares a card type with an exiled card, "exactly thirteen permanents", a return tapped and attacking that
 * grants a state trigger, revealing the card looked at, a search after "then", a basic land sharing no land type, play-or-cast
 * triggers checking the exiled card, and a cast trigger for a spell named like an exiled card.
 */
class VowChunk09EffectsTest {
    private val exiled = Objects.Bound("exiled")
    private val graveyardCard = and(cardFilter, inZone(Zone.Graveyard))
    private val creatureYouDontControl = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))
    private val exileAGraveyardCard = arrayOf(
        Step.Choose(Choice.Objects(graveyardCard, n(1), n(1)), bind = "chosen", chooser = Players.You),
        move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
        doing(Effect.Remember("exiled", exiled)),
    )
    private val playLandOrCast = { who: PlayerFilter -> Trigger.On(listOf(EventPattern.PlayLand(ObjectFilter.Any, who), EventPattern.Cast(ObjectFilter.Any, who))) }
    private val sharesWithExiled = Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.Shares(SharedQuality.CardType, exiledWith))
    private val seen = Objects.Bound("seen")
    private val kept = Objects.Bound("kept")
    private fun lookAt(n: Int) = doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(n), fromTop = true), players = Players.You, bind = "seen"))
    private fun keep(n: Int) = Step.Choose(Choice.Objects(ObjectFilter.Any, n(n), n(n), from = seen), bind = "kept", chooser = Players.You)

    // CR 613.4c (layer 7c: each creature you don't control gets -1/-1 for each slime counter on that creature); CR 603.10a (a creature that
    // died with a slime counter on it, as it last existed); CR 122.1
    @Test
    fun toxrillTheCorrosive() = card(
        name = "Vorlax, the Dissolving",
        cost = "{5}{B}{B}",
        type = "Legendary Creature — Slug Horror",
        pt = "7/7",
        text = """
            At the beginning of each end step, put a slime counter on each creature you don't control.
            Creatures you don't control get -1/-1 for each slime counter on them.
            Whenever a creature you don't control with a slime counter on it dies, create a 1/1 black Slug creature token.
            {U}{B}, Sacrifice a Slug: Draw a card.
        """,
    ) parsesTo CounterKind.Named("slime").let { slime ->
        val each = Value.Times(Value.Of(NumericProperty.Counters(slime), Objects.Affected, Combine.Single), -1)
        abilities(
            triggered(stepBegins(TurnStep.End, PlayerFilter.Any), counters(Objects.Each(creatureYouDontControl), 1, slime)),
            static(Mechanic.ModifyPowerToughness(each, each), affects = Objects.Each(creatureYouDontControl)),
            triggered(dies(and(creatureYouDontControl, ObjectFilter.HasCounter(slime, atLeast = 1))),
                create(creatureToken("Slug", listOf(Color.Black), 1, 1))),
            activated(cost(mana("{U}{B}"), sacrificeA(sub("Slug"))), draw(1)),
        )
    }

    // CR 607.2a (the abilities of the cards this creature's own ability exiled), CR 613.1f (layer 6), CR 113.6
    @Test
    fun patchworkCrawler() = card(
        name = "Stitchwork Skitterer",
        cost = "{1}{U}",
        type = "Creature — Zombie Horror",
        pt = "1/2",
        text = """
            {2}{U}: Exile target creature card from your graveyard and put a +1/+1 counter on this creature.
            This creature has all activated abilities of all creature cards exiled with it.
        """,
    ) parsesTo abilities(
        activated(manaCost("{2}{U}"),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", exiled)),
            counters(Objects.Self),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
        static(Mechanic.GainAbilitiesOf(Objects.Where(exiledWith, creature))),
    )

    // CR 701.24a (shuffle), CR 401.4 (looked-at cards: one into the hand, the rest on the bottom in a random order), CR 115.1
    @Test
    fun witnessTheFuture() = card(
        name = "Glimpse of Tomorrows",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Target player shuffles up to four target cards from their graveyard into their library. You look at the top four cards of your library, then put one of those cards into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            move(target("cards"), Zone.Library),
            doing(Effect.Shuffle(Players.Target("player"))),
            lookAt(4),
            keep(1),
            move(kept, Zone.Hand),
            doing(Effect.Move(Objects.Except(seen, kept), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            targets = listOf(targetPlayer, slot("cards", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))),
                min = 0, max = 4)),
        ),
    )

    // CR 608.2c (in the order written: draw, lose life, then create the token); CR 111.10g (Blood)
    @Test
    fun pointedDiscussion() = card(
        name = "Barbed Conversation",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "You draw two cards, lose 2 life, then create a Blood token. (It's an artifact with \"{1}, {T}, Discard a card, Sacrifice this token: Draw a card.\")",
    ) parsesTo abilities(
        spell(draw(2), loseLife(2), create(blood)),
    )

    // CR 601.3 (cast from the top of the library once each turn, CR 401.5 looking at it any time); CR 607.2a, 205.2a (shares a card type
    // with a card this creature exiled)
    @Test
    fun cemeteryIlluminator() = card(
        name = "Gravelight Wisp",
        cost = "{1}{U}{U}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Flying
            Whenever this creature enters or attacks, exile a card from a graveyard.
            You may look at the top card of your library any time.
            Once each turn, you may cast a spell from the top of your library if it shares a card type with a card exiled with this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf, *exileAGraveyardCard),
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, perTurn = 1, holder = PlayerFilter.You),
            affects = Objects.Each(and(cardFilter, ObjectFilter.Shares(SharedQuality.CardType, exiledWith), inZone(Zone.Library),
                ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 603.12 (the reflexive trigger only when the enchantment was sacrificed), CR 608.2c (the count checked after the token is created),
    // CR 701.21a
    @Test
    fun stensiaUprising() = card(
        name = "Highland Revolt",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = "At the beginning of your end step, create a 1/1 red Human creature token. Then if you control exactly thirteen permanents, you may sacrifice this enchantment. When you do, it deals 7 damage to any target.",
    ) parsesTo abilities(
        triggered(yourEndStep,
            create(creatureToken("Human", listOf(Color.Red), 1, 1)),
            ifThen(Condition.Compare(count(and(permanentFilter, you)), Comparison.Equal, n(13)),
                Step.Pay(Cost(listOf(sacrificeSelf)), bind = "paid", payer = Players.You),
                ifThen(Condition.Did("paid"), reflexive(damage(7, toAny), targets = listOf(anyTarget)))),
        ),
    )

    // CR 401.4 (two of the cards looked at into the hand, the rest into the graveyard)
    @Test
    fun scatteredThoughts() = card(
        name = "Strewn Musings",
        cost = "{3}{U}",
        type = "Instant",
        text = "Look at the top four cards of your library. Put two of those cards into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        spell(lookAt(4), keep(2), move(kept, Zone.Hand), move(Objects.Except(seen, kept), Zone.Graveyard)),
    )

    // CR 508.4 (put onto the battlefield attacking: never declared as an attacker), CR 603.8 (the granted state trigger: whenever its controller
    // controls no legendary Vampire), CR 611.2a (granted indefinitely), CR 109.5 ("you" of the granted ability is the creature's controller)
    @Test
    fun oliviaCrimsonBride() = card(
        name = "Seraphine, Scarlet Bride",
        cost = "{4}{B}{R}",
        type = "Legendary Creature — Vampire Noble",
        pt = "3/4",
        text = """
            Flying, haste
            Whenever Seraphine attacks, return target creature card from your graveyard to the battlefield tapped and attacking. It gains "When you don't control a legendary Vampire, exile this creature."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(attacksSelf,
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, tapped = true, attacking = AttackTarget.Chosen), postBind = "returned")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Ability.Triggered(
                Trigger.State(Condition.Not(Condition.Exists(and(legendary, sub("Vampire"), you), atLeast = 1))),
                Instructions(steps = listOf(move(Objects.Self, Zone.Exile))))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 701.20a (reveal), CR 118.12 ("If you do": only when the card was revealed and put into the hand), CR 202.3 (its mana value); CR 606.3
    @Test
    fun sorinTheMirthless() = card(
        name = "Valdric the Joyless",
        cost = "{2}{B}{B}",
        type = "Legendary Planeswalker — Sorin",
        loyalty = "4",
        text = """
            [+1]: Look at the top card of your library. You may reveal that card and put it into your hand. If you do, you lose life equal to its mana value.
            [−2]: Create a 2/3 black Vampire creature token with flying and lifelink.
            [−7]: Valdric deals 13 damage to any target. You gain 13 life.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), players = Players.You, bind = "seen")),
            Step.Pay(steps = listOf(doing(Effect.Reveal(seen)), move(seen, Zone.Hand)), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), doing(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, seen, Combine.Single))))),
        activated(cost(CostPart.Loyalty(n(-2))),
            create(creatureToken("Vampire", listOf(Color.Black), 2, 3, abilities = listOf(Keyword.Flying, Keyword.Lifelink)))),
        activated(cost(CostPart.Loyalty(n(-7))), damage(13, toAny), gainLife(13), targets = listOf(anyTarget)),
    )

    // CR 608.2c (the exile first, then the search); CR 701.23a, 701.24a
    @Test
    fun demonicBargain() = card(
        name = "Infernal Wager",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Exile the top thirteen cards of your library, then search your library for a card. Put that card into your hand, then shuffle.",
    ) parsesTo abilities(
        spell(
            move(Objects.LibraryCards(Players.You, n(13), fromTop = true), Zone.Exile, postBind = "exiled"),
            search(cardFilter),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle,
        ),
    )

    // CR 205.3i (land types, a basic land card sharing none with the lands you control), CR 701.23a; CR 305.6 (basic land types counted once)
    @Test
    fun hiveheartShaman() = card(
        name = "Broodcall Mystic",
        cost = "{3}{G}",
        type = "Creature — Human Shaman",
        pt = "3/5",
        text = """
            Whenever this creature attacks, you may search your library for a basic land card that doesn't share a land type with a land you control, put that card onto the battlefield, then shuffle.
            {5}{G}: Create a 1/1 green Insect creature token. Put X +1/+1 counters on it, where X is the number of basic land types among lands you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, Step.May(listOf(
            search(and(basicLand(), not(ObjectFilter.Shares(SharedQuality.LandType, Objects.Each(and(land, you)))))),
            move(Objects.Bound("found"), Zone.Battlefield),
            shuffle,
        ), Players.You)),
        activated(manaCost("{5}{G}"),
            create(creatureToken("Insect", listOf(Color.Green), 1, 1), bind = "token"),
            counters(Objects.Bound("token"), Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType)),
            rules = asSorcery),
    )

    // CR 603.4 (intervening if: the land played or the spell cast shares a card type with the exiled card), CR 607.2a, 305.1, 601.2i
    @Test
    fun cemeteryProtector() = card(
        name = "Gravesward Sentinel",
        cost = "{2}{W}{W}",
        type = "Creature — Human Soldier",
        pt = "3/4",
        text = """
            Flash
            When this creature enters, exile a card from a graveyard.
            Whenever you play a land or cast a spell, if it shares a card type with the exiled card, create a 1/1 white Human creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, *exileAGraveyardCard),
        triggered(playLandOrCast(PlayerFilter.You), create(creatureToken("Human", listOf(Color.White), 1, 1)), interveningIf = sharesWithExiled),
    )

    // CR 603.4, 607.2a; CR 102.1 (any player, "that player" the one who played or cast it)
    @Test
    fun cemeteryGatekeeper() = card(
        name = "Gravesward Warden",
        cost = "{1}{R}",
        type = "Creature — Vampire",
        pt = "2/1",
        text = """
            First strike
            When this creature enters, exile a card from a graveyard.
            Whenever a player plays a land or casts a spell, if it shares a card type with the exiled card, this creature deals 2 damage to that player.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(entersSelf, *exileAGraveyardCard),
        triggered(playLandOrCast(PlayerFilter.Any), damage(2, Recipients(players = Players.EventPlayer)), interveningIf = sharesWithExiled),
    )

    // CR 610.3 (exiled until this leaves), CR 607.2a (cards exiled with this enchantment), CR 201.2 (the same name)
    @Test
    fun circleOfConfinement() = card(
        name = "Ring of Detainment",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile target creature an opponent controls with mana value 3 or less until this enchantment leaves the battlefield.
            Whenever an opponent casts a Vampire spell with the same name as a card exiled with this enchantment, you gain 2 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self, postBind = "exiled")),
            doing(Effect.Remember("exiled", exiled)),
            targets = listOf(slot("creature", and(creature, opponents, manaValue(Comparison.AtMost, 3))))),
        triggered(on(EventPattern.Cast(and(sub("Vampire"), spellFilter, ObjectFilter.SameNameAs(exiledWith)), PlayerFilter.Opponent)), gainLife(2)),
    )
}
