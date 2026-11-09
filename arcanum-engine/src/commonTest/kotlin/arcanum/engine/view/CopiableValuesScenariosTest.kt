package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CopyException
import arcanum.api.Door
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.CopiableValues
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An object's copiable values as a whole: layout and all faces (CR 707.2); designations and status stay on the object (SYS-080). */
class CopiableValuesScenariosTest {
    private val room = Cards.inventFaces("Split",
        face("Lantern Foyer", "{1}{W}", "Enchantment — Room", "Other creatures you control get +1/+1.", null, null, null),
        face("Quiet Pantry", "{2}{B}", "Enchantment — Room", "Creatures you control have flying.", null, null, null),
    )
    private val seraph = Cards.inventFaces("Prepare",
        face("Hushed Seraph", "{3}{W}", "Creature — Angel", "{0}: This creature becomes prepared.", "2/4", null, null),
        face("Small Mending", "{G}", "Sorcery", "You gain 1 life.", null, null, null),
    )
    private val werewolf = Cards.inventFaces("Transform",
        face("Pale Pup", "{1}{G}", "Creature — Wolf", "{0}: Transform this creature.", "1/1", null, null),
        face("Pale Howler", null, "Creature — Wolf", "{0}: Transform this creature.", "5/5", null, null),
    )
    private val apprentice = Cards.define("Tin Apprentice", CardDefinition(Layout.Flip, listOf(
        FaceDefinition("Tin Apprentice", ManaCost.parse("{U}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1)),
        FaceDefinition("Tin Archmage", types = listOf(CardType.Creature), power = PrintedNumber.Fixed(4), toughness = PrintedNumber.Fixed(4)),
    )))
    private val novice = Cards.define("Glass Novice", CardDefinition(Layout.Flip, listOf(
        FaceDefinition("Glass Novice", types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1)),
        FaceDefinition("Glass Master", types = listOf(CardType.Creature), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3)),
    )))
    /** "Flip target creature." */
    private val turningWord = Cards.define("Turning Word", CardDefinition(Layout.Normal, listOf(FaceDefinition("Turning Word", ManaCost.parse("{0}"),
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.Flip(Objects.Target("creature")))))))))))
    private val bear = Cards.invent("Moss Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val clay = Cards.invent("Soft Clay", "{0}", "Creature — Shapeshifter", "", "1/1")
    private val blank = Cards.invent("Blank Panel", "{0}", "Enchantment", "")
    private val press = Cards.invent("Door Press", "{0}", "Artifact", "{0}: Create a token that's a copy of target enchantment you control.")
    private val mold = Cards.invent("Wax Mold", "{0}", "Artifact", "{0}: Create a token that's a copy of target creature you control.")
    private val copyMechanic = Mechanic.CopyOf(Objects.Bound("original"))

    private fun Scenario.unlocks(id: ObjectId) = Engine.options(state, alice.id).filterIsInstance<Input.Activate>()
        .filter { it.source == id && (it.ability as? AbilityOrigin.Rule)?.name?.startsWith("unlock-") == true }

    private fun Scenario.seen(id: ObjectId) = Engine.view(state)[id]!!

    private fun Scenario.token() = state.battlefield.first { state.objOrThrow(it).token }

    /** Performs the only ability [id] can activate now and lets it resolve. */
    private fun Scenario.activateOnly(id: ObjectId) {
        val ability = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().first { it.source == id }
        perform(Input.Activate(alice.id, id, ability.ability))
        allPass()
    }

    private fun Scenario.unlockLeftOf(name: String) {
        val id = card(name, zone = Zone.Battlefield) as ObjectId.Card
        state.objects[id] = state.objOrThrow(id).copy(marks = listOf(ObjectMark.Unlocked(Door.Left)))
    }

    // ---- Rooms

    // CR 709.5, 709.5b, 709.5c: the halves are copiable, the designations aren't: a copy of a Room with its left door unlocked
    // has both halves, both locked
    @Test
    fun aPermanentThatBecomesACopyOfAnUnlockedRoomHasBothDoorsLocked() = scenario {
        alice.battlefield(room, blank)
        unlockLeftOf(room)
        effect(alice, copyMechanic, on = listOf(blank), names = mapOf("original" to room))
        assertEquals("Lantern Foyer", seen(card(room)).characteristics!!.name, "the original keeps its unlocked door")
        val c = seen(card(blank)).characteristics!!
        assertNull(c.name, "both doors of the copy are locked: no name")
        assertNull(c.manaCost, "both doors of the copy are locked: no mana cost")
        assertTrue(c.abilities.isEmpty(), "both doors of the copy are locked: no rules text")
        assertEquals(listOf(CardType.Enchantment), c.types, "CR 709.5a: the shared types stay")
    }

    // CR 709.5e: the controller of a copy of a Room may pay a locked half's mana cost to unlock it; then that half is there
    @Test
    fun aCopyOfARoomCanBeUnlockedHalfByHalf() = scenario {
        alice.battlefield(room, blank, "Plains", "Plains", "Swamp", "Swamp", "Swamp")
        effect(alice, copyMechanic, on = listOf(blank), names = mapOf("original" to room))
        val copy = card(blank)
        assertEquals(2, unlocks(copy).size, "both doors can be unlocked")
        alice.takesSpecialAction(blank, "unlock-right", paying = listOf("Swamp", "Swamp", "Swamp"))
        assertEquals(listOf<ObjectMark>(ObjectMark.Unlocked(Door.Right)), state.objOrThrow(copy).marks)
        assertEquals("Quiet Pantry", seen(copy).characteristics!!.name)
        assertEquals(1, seen(copy).characteristics!!.abilities.size)
        assertEquals(1, unlocks(copy).size, "the other door stays locked")
    }

    // CR 707.5, 709.5b, 709.5d: entering as a copy of a Room, neither half was cast: both locked, and they can be unlocked
    @Test
    fun aPermanentEnteringAsACopyOfARoomHasBothDoorsLocked() = scenario {
        val entering = Cards.invent("Mimic Panel", "{0}", "Enchantment", "You may have this enchantment enter as a copy of an enchantment on the battlefield.")
        alice.battlefield(room, "Plains", "Swamp", "Swamp", "Swamp")
        unlockLeftOf(room)
        alice.hand(entering)
        alice.casts(entering)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(room)
        val copy = state.battlefield.first { state.objOrThrow(it).definition?.faces?.first()?.name == entering }
        val v = seen(copy)
        assertEquals(Layout.Split, v.copiable!!.layout)
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), v.copiable!!.faces.map { it.name })
        assertNull(v.characteristics!!.name)
        assertEquals(2, unlocks(copy).size)
    }

    // CR 709.5b, 709.5d, 111.3: a token copy of a Room has two locked halves, whichever door the original has unlocked
    @Test
    fun aTokenCopyOfARoomHasTwoLockedHalves() = scenario {
        alice.battlefield(room, press, "Plains", "Swamp", "Swamp", "Swamp")
        unlockLeftOf(room)
        alice.activates(press, target = room)
        allPass()
        val token = token()
        val o = state.objOrThrow(token)
        assertEquals(Layout.Split, o.definition!!.layout)
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), o.definition!!.faces.map { it.name })
        assertTrue(o.marks.isEmpty(), "CR 709.5c: the designation isn't copied")
        assertNull(seen(token).characteristics!!.name)
        assertEquals(2, unlocks(token).size, "both doors of the token can be unlocked")
        change(Changes.move(state, listOf(card(room, 0)), Zone.Graveyard))
        alice.takesSpecialAction(room, "unlock-right", paying = listOf("Swamp", "Swamp", "Swamp"))
        assertEquals("Quiet Pantry", seen(token).characteristics!!.name)
    }

    // CR 709.5f, g: effects that unlock and lock doors work on a copy of a Room, and choose among its copied halves
    @Test
    fun effectsLockAndUnlockTheDoorsOfACopyOfARoom() = scenario {
        val key = Cards.invent("Skeleton Key", "{0}", "Artifact", "{0}: Lock or unlock a door of target Room you control.")
        alice.battlefield(room, blank, key)
        effect(alice, copyMechanic, on = listOf(blank), names = mapOf("original" to room))
        val copy = card(blank)
        alice.activates(key, target = blank)
        allPass()
        answer(Answer.Option("unlock"))
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), (pending as Decision.ChooseOption).options)
        answer(Answer.Option("Quiet Pantry"))
        allPass()
        assertEquals(listOf<ObjectMark>(ObjectMark.Unlocked(Door.Right)), state.objOrThrow(copy).marks)
        alice.activates(key, target = blank)
        allPass()
        answer(Answer.Option("lock"))
        allPass()
        assertTrue(state.objOrThrow(copy).marks.isEmpty(), "its one open door is locked again")
    }

    // CR 707.2, 110.5c, 709.5c: a Room that becomes a copy of a one-faced permanent has no doors; its designation stays on it
    @Test
    fun aRoomThatBecomesACopyOfAOneFacedPermanentHasNoDoors() = scenario {
        alice.battlefield(room, blank)
        unlockLeftOf(room)
        effect(alice, copyMechanic, on = listOf(room), names = mapOf("original" to blank))
        val v = seen(card(room))
        assertEquals("Blank Panel", v.characteristics!!.name)
        assertEquals(Layout.Normal, v.copiable!!.layout)
        assertTrue(unlocks(v.id).isEmpty())
        assertEquals(listOf<ObjectMark>(ObjectMark.Unlocked(Door.Left)), v.obj!!.marks)
    }

    // ---- Preparation

    // CR 722.2b, 722.3a: the prepare spell is part of the copiable values, so a copy of a preparation creature can become prepared;
    // CR 722.3c: its controller creates the copy in exile, of the prepare spell
    @Test
    fun aCopyOfAPreparationCreatureCanBecomePrepared() = scenario {
        alice.battlefield(seraph, clay)
        effect(alice, copyMechanic, on = listOf(clay), names = mapOf("original" to seraph))
        val copy = card(clay)
        activateOnly(copy)
        assertTrue(ObjectMark.Prepared in state.objOrThrow(copy).marks, "the copy becomes prepared")
        assertFalse(ObjectMark.Prepared in state.objOrThrow(card(seraph)).marks, "CR 722.3a: the designation is the object's own")
        val spell = state.exile.single()
        assertEquals("Small Mending", seen(spell).characteristics!!.name)
        assertEquals<ObjectId?>(copy, state.objOrThrow(spell).prepareSpellOf)
    }

    // CR 707.2, 722.3a: the prepared designation isn't copied
    @Test
    fun theCopyOfAPreparedPermanentIsNotPrepared() = scenario {
        alice.battlefield(seraph, mold)
        activateOnly(card(seraph))
        assertTrue(ObjectMark.Prepared in state.objOrThrow(card(seraph)).marks)
        alice.activates(mold, target = seraph)
        allPass()
        assertFalse(ObjectMark.Prepared in state.objOrThrow(token()).marks)
    }

    // CR 722.2b, 722.3c: a token copy has the prepare spell too; its exceptions don't reach the spell (the rule's example)
    @Test
    fun aTokenCopyOfAPreparationCreatureHasThePrepareSpellAsPrinted() = scenario {
        /** "Create a token that's a copy of target creature, except it's a 1/1." */
        val frogWord = Cards.define("Frog Word", CardDefinition(Layout.Normal, listOf(FaceDefinition("Frog Word", ManaCost.parse("{0}"),
            types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(Objects.Target("creature"), listOf(CopyException.PowerToughness(1, 1))),
                    arcanum.api.Value.Fixed(1), arcanum.api.Players.You))))))))))
        alice.battlefield(seraph)
        alice.hand(frogWord)
        alice.castsWithoutCosts(frogWord, target = seraph)
        resolve()
        val token = token()
        assertEquals(1, seen(token).characteristics!!.power)
        activateOnly(token)
        assertTrue(ObjectMark.Prepared in state.objOrThrow(token).marks)
        val spell = seen(state.exile.single()).characteristics!!
        assertEquals("Small Mending", spell.name)
        assertNull(spell.power)
        assertEquals(listOf(CardType.Sorcery), spell.types)
    }

    // ---- Double-faced

    // CR 707.8a: a token copy of a double-faced permanent is double-faced and can transform
    @Test
    fun aTokenCopyOfADoubleFacedPermanentCanTransform() = scenario {
        alice.battlefield(werewolf, mold)
        alice.activates(mold, target = werewolf)
        allPass()
        val token = token()
        assertEquals(Layout.Transform, state.objOrThrow(token).definition!!.layout)
        assertEquals("Pale Pup", seen(token).characteristics!!.name)
        activateOnly(token)
        assertEquals("Pale Howler", seen(token).characteristics!!.name)
        assertEquals(5, seen(token).characteristics!!.power)
        assertTrue(state.objOrThrow(token).transformed)
    }

    // CR 707.8a: "If the token is a copy of a double-faced permanent with its back face up, the token enters the battlefield with
    // its back face up"
    @Test
    fun aTokenCopyOfATransformedPermanentEntersWithItsBackFaceUp() = scenario {
        alice.battlefield(werewolf, mold)
        activateOnly(card(werewolf))
        assertEquals("Pale Howler", seen(card(werewolf)).characteristics!!.name)
        alice.activates(mold, target = werewolf)
        allPass()
        val token = token()
        assertEquals(1, state.objOrThrow(token).face)
        assertEquals("Pale Howler", seen(token).characteristics!!.name)
        activateOnly(token)
        assertEquals("Pale Pup", seen(token).characteristics!!.name, "it can transform back")
    }

    // CR 707.8, 712.9, 701.27c: a one-faced permanent copying a double-faced one has the face up's values and can't transform;
    // CR 707.8a's example of Clone: a token copy of that permanent isn't double-faced either
    @Test
    fun aOneFacedCopyOfADoubleFacedPermanentCannotTransform() = scenario {
        alice.battlefield(werewolf, clay, mold)
        effect(alice, copyMechanic, on = listOf(clay), names = mapOf("original" to werewolf))
        val copy = card(clay)
        assertEquals("Pale Pup", seen(copy).characteristics!!.name)
        assertEquals(Layout.Normal, seen(copy).copiable!!.layout)
        activateOnly(copy)
        assertEquals("Pale Pup", seen(copy).characteristics!!.name, "nothing happens")
        alice.activates(mold, target = clay)
        allPass()
        val token = state.objOrThrow(token())
        assertEquals(Layout.Normal, token.definition!!.layout)
        assertEquals(listOf("Pale Pup"), token.definition!!.faces.map { it.name })
    }

    // CR 707.8a: "as modified by any other copy effects": each face of the token has what the copy effect gives
    @Test
    fun aTokenCopyOfADoubleFacedPermanentUnderACopyEffectIsDoubleFacedWithTheCopiedValues() = scenario {
        alice.battlefield(werewolf, bear, mold)
        effect(alice, copyMechanic, on = listOf(werewolf), names = mapOf("original" to bear))
        alice.activates(mold, target = werewolf)
        allPass()
        val o = state.objOrThrow(token())
        assertEquals(Layout.Transform, o.definition!!.layout)
        assertEquals(listOf("Moss Bear", "Moss Bear"), o.definition!!.faces.map { it.name })
    }

    // ---- Flip

    // CR 110.5c's example, CR 707.3: a permanent that's a copy of a flip card flips into its flipped version
    @Test
    fun aCopyOfAFlipCardCanFlip() = scenario {
        alice.battlefield(apprentice, clay)
        alice.hand(turningWord)
        val copy = card(clay)
        effect(alice, copyMechanic, on = listOf(clay), names = mapOf("original" to apprentice), duration = Duration.Indefinitely)
        assertEquals("Tin Apprentice", seen(copy).characteristics!!.name)
        assertEquals(Layout.Flip, seen(copy).copiable!!.layout)
        alice.castsWithoutCosts(turningWord, target = clay)
        resolve()
        assertEquals("Tin Archmage", seen(copy).characteristics!!.name)
        assertEquals(4, seen(copy).characteristics!!.power)
        assertTrue(state.objOrThrow(copy).flipped)
    }

    // CR 110.5c's example: flipped status stays while the permanent copies something else, of no relevance then; CR 707.3's example:
    // copying an unflipped flip card, a flipped permanent has that card's flipped version
    @Test
    fun aFlippedPermanentKeepsItsStatusWhateverItCopies() = scenario {
        alice.battlefield(novice, bear, clay)
        val copy = card(clay) as ObjectId.Card
        state.objects[copy] = state.objOrThrow(copy).copy(flippedStatus = true)
        effect(alice, copyMechanic, on = listOf(clay), names = mapOf("original" to bear), duration = Duration.Indefinitely)
        assertEquals("Moss Bear", seen(copy).characteristics!!.name)
        assertTrue(state.objOrThrow(copy).flipped)
        effect(alice, copyMechanic, on = listOf(clay), names = mapOf("original" to novice), duration = Duration.Indefinitely)
        assertEquals("Glass Master", seen(copy).characteristics!!.name)
    }

    // CR 707.2, 110.5b: status isn't copied: a token copy of a flipped permanent enters unflipped, and has both versions
    @Test
    fun aTokenCopyOfAFlippedPermanentEntersUnflipped() = scenario {
        alice.battlefield(apprentice, mold)
        alice.hand(turningWord)
        alice.castsWithoutCosts(turningWord, target = apprentice)
        resolve()
        assertEquals("Tin Archmage", seen(card(apprentice)).characteristics!!.name)
        alice.activates(mold, target = apprentice)
        allPass()
        val token = token()
        assertEquals(Layout.Flip, state.objOrThrow(token).definition!!.layout)
        assertEquals("Tin Apprentice", seen(token).characteristics!!.name)
        assertFalse(state.objOrThrow(token).flipped)
    }

    // CR 110.5c, 707.3, 712.8e: a double-faced permanent with its back face up never flipped: copying a flip card, it has the unflipped version
    @Test
    fun aTransformedPermanentCopyingAFlipCardIsUnflipped() = scenario {
        alice.battlefield(werewolf, novice)
        val id = card(werewolf) as ObjectId.Card
        state.objects[id] = state.objOrThrow(id).copy(face = 1)
        effect(alice, copyMechanic, on = listOf(werewolf), names = mapOf("original" to novice), duration = Duration.Indefinitely)
        assertEquals("Glass Novice", seen(id).characteristics!!.name)
        assertFalse(state.objOrThrow(id).flipped)
    }

    // CR 110.5c, 709.5d: nor did a Room whose right half was cast
    @Test
    fun aRoomCastAsItsRightHalfCopyingAFlipCardIsUnflipped() = scenario {
        alice.battlefield(novice, room)
        val id = card(room) as ObjectId.Card
        state.objects[id] = state.objOrThrow(id).copy(face = 1, marks = listOf(ObjectMark.Unlocked(Door.Right)))
        effect(alice, copyMechanic, on = listOf(room), names = mapOf("original" to novice), duration = Duration.Indefinitely)
        assertEquals("Glass Novice", seen(id).characteristics!!.name)
    }

    /** [name] becomes a copy of the flip card [novice] and flips; then that copy effect ends. */
    private fun Scenario.flipsAsACopyAndStopsCopying(name: String) {
        val id = card(name)
        val copying = effect(alice, copyMechanic, on = listOf(name), names = mapOf("original" to novice), duration = Duration.Indefinitely)
        alice.hand(turningWord)
        alice.castsWithoutCosts(turningWord, target = name)
        resolve()
        assertEquals("Glass Master", seen(id).characteristics!!.name)
        assertTrue(state.objOrThrow(id).flipped)
        change(Change.EffectRemoved(state.effects[copying]!!))
        assertTrue(state.objOrThrow(id).flipped, "CR 110.5c: it stays flipped")
    }

    // CR 110.5c, 712.8d: a double-faced permanent that flips while copying a flip card still has its front face up afterwards
    @Test
    fun aDoubleFacedPermanentThatFlippedAsACopyStaysFrontFaceUp() = scenario {
        alice.battlefield(werewolf, novice)
        flipsAsACopyAndStopsCopying(werewolf)
        val id = card(werewolf)
        assertEquals("Pale Pup", seen(id).characteristics!!.name)
        assertEquals(0, state.objOrThrow(id).face)
        assertFalse(state.objOrThrow(id).transformed)
    }

    // CR 110.5c, 722.4: a preparation permanent has only its normal characteristics whatever its flipped status
    @Test
    fun aPreparationPermanentThatFlippedAsACopyKeepsItsNormalCharacteristics() = scenario {
        alice.battlefield(seraph, novice)
        flipsAsACopyAndStopsCopying(seraph)
        assertEquals("Hushed Seraph", seen(card(seraph)).characteristics!!.name)
    }

    // CR 707.2, 110.5b: status isn't copied, whatever the original is
    @Test
    fun aTokenCopyOfAOnceFlippedPermanentEntersUnflipped() = scenario {
        alice.battlefield(clay, novice, mold)
        flipsAsACopyAndStopsCopying(clay)
        alice.activates(mold, target = clay)
        allPass()
        assertFalse(state.objOrThrow(token()).flipped)
        assertEquals(0, state.objOrThrow(token()).face)
    }

    // CR 400.7, 110.5d: only permanents have status: a new object starts without it
    @Test
    fun theFlippedStatusEndsWithTheObject() = scenario {
        alice.battlefield(clay, novice)
        flipsAsACopyAndStopsCopying(clay)
        change(Changes.move(state, listOf(card(clay)), Zone.Graveyard))
        assertFalse(state.objOrThrow(card(clay)).flipped)
    }

    // ---- Becoming a copy fixes the values

    private val shifter = Cards.invent("Shift Beast", "{0}", "Creature — Shapeshifter", "{0}: This creature becomes a copy of target creature until end of turn.", "1/1")

    // CR 707.2b: the copy keeps the values it got when the original dies
    @Test
    fun aBecomesACopyEffectOutlivesTheOriginal() = scenario {
        val end = Cards.invent("Abrupt Copybreak", "{0}", "Instant", "Destroy target creature.")
        alice.battlefield(shifter, bear)
        alice.hand(end)
        val id = card(shifter)
        alice.activates(shifter, target = bear)
        allPass()
        assertEquals("Moss Bear", seen(id).characteristics!!.name)
        alice.castsWithoutCosts(end, target = bear)
        resolve()
        assertEquals("Moss Bear", seen(id).characteristics!!.name, "after the original died")
    }

    // CR 707.2b, 707.8: the copy keeps the face that was up when copied
    @Test
    fun aBecomesACopyEffectDoesNotFollowTheOriginalTransforming() = scenario {
        alice.battlefield(shifter, werewolf)
        val id = card(shifter)
        alice.activates(shifter, target = werewolf)
        allPass()
        assertEquals("Pale Pup", seen(id).characteristics!!.name)
        activateOnly(card(werewolf))
        assertEquals("Pale Howler", seen(card(werewolf)).characteristics!!.name)
        assertEquals("Pale Pup", seen(id).characteristics!!.name, "after the original transformed")
    }

    // CR 707.2b, 722.2b: the fixed values are whole: a copy of a preparation creature still has the prepare spell once the original is gone
    @Test
    fun aBecomesACopyEffectFixesLayoutAndFaces() = scenario {
        alice.battlefield(shifter, seraph)
        val id = card(shifter)
        alice.activates(shifter, target = seraph)
        allPass()
        change(Changes.move(state, listOf(card(seraph, 0)), Zone.Graveyard))
        assertEquals(Layout.Prepare, seen(id).copiable!!.layout)
        assertEquals(listOf("Hushed Seraph", "Small Mending"), seen(id).copiable!!.faces.map { it.name })
    }

    // ---- Abilities gained in copying

    private val copyWithFlying = Mechanic.CopyOf(Objects.Bound("original"), listOf(CopyException.AddAbilities(listOf(arcanum.api.Keyword.Flying))))

    private fun Scenario.flyingOn(id: ObjectId) = seen(id).characteristics!!.abilities.count { it.ability == arcanum.api.Keyword.Flying }

    // CR 707.9a, 709.5: an ability gained in copying isn't the rules text of either half: it stays with both doors locked
    @Test
    fun anAddedAbilityWorksWhileBothDoorsAreLocked() = scenario {
        alice.battlefield(room, blank)
        effect(alice, copyWithFlying, on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        val v = seen(card(blank))
        assertNull(v.characteristics!!.name)
        assertEquals(1, flyingOn(v.id))
        assertEquals(listOf<arcanum.api.Ability>(arcanum.api.Keyword.Flying), v.copiable!!.gained)
    }

    // CR 707.9a, 113.2c: one ability, once, with both doors open, and with one
    @Test
    fun anAddedAbilityIsThereOnceWhicheverDoorsAreOpen() = scenario {
        alice.battlefield(room, blank)
        effect(alice, copyWithFlying, on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        val id = card(blank) as ObjectId.Card
        state.objects[id] = state.objOrThrow(id).copy(marks = listOf(ObjectMark.Unlocked(Door.Left), ObjectMark.Unlocked(Door.Right)))
        assertEquals(1, flyingOn(id))
        state.objects[id] = state.objOrThrow(id).copy(marks = listOf(ObjectMark.Unlocked(Door.Right)))
        assertEquals(1, flyingOn(id))
        assertEquals("Quiet Pantry", seen(id).characteristics!!.name)
    }

    // CR 707.3, 707.9a: the ability is part of the copiable values: a copy of that copy has it, as does a token copy, apart from its doors
    @Test
    fun anAddedAbilityGoesOnToCopiesOfTheCopy() = scenario {
        val other = Cards.invent("Second Panel", "{0}", "Enchantment", "")
        alice.battlefield(room, blank, other, press)
        effect(alice, copyWithFlying, on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        effect(alice, copyMechanic, on = listOf(other), names = mapOf("original" to blank), duration = Duration.Indefinitely)
        assertEquals(1, flyingOn(card(other)))
        alice.activates(press, target = blank)
        allPass()
        val token = token()
        assertEquals(1, flyingOn(token))
        assertEquals(listOf<arcanum.api.Ability>(arcanum.api.Keyword.Flying), state.objOrThrow(token).gainedInCopying)
        assertNull(seen(token).characteristics!!.name)
    }

    // CR 707.9a: on a token created with the exception too; a double-faced token has it on each face, one up at a time
    @Test
    fun anExceptionAddingAnAbilityOnATokenCopy() = scenario {
        /** "Create a token that's a copy of target permanent, except it has flying." */
        val word = Cards.define("Wing Word", CardDefinition(Layout.Normal, listOf(FaceDefinition("Wing Word", ManaCost.parse("{0}"),
            types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
                targets = listOf(TargetSlot("thing", TargetKind.Objects(ObjectFilter.Any), arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(Objects.Target("thing"),
                    listOf(CopyException.AddAbilities(listOf(arcanum.api.Keyword.Flying)))), arcanum.api.Value.Fixed(1), arcanum.api.Players.You))))))))))
        alice.battlefield(room)
        alice.hand(word)
        alice.castsWithoutCosts(word, target = room)
        resolve()
        assertEquals(1, flyingOn(token()))
    }

    // ---- Merged permanents

    // CR 702.140e, 707.2: a merged permanent's copiable values have every component's abilities, for a token copy too
    @Test
    fun aTokenCopyOfAMergedPermanentHasEveryAbility() = scenario {
        val flyer = Cards.invent("Sky Part", "{0}", "Creature — Bird", "Flying", "1/1")
        alice.battlefield(clay, flyer, mold)
        val id = card(clay) as ObjectId.Card
        val part = state.objOrThrow(card(flyer))
        state.objects[id] = state.objOrThrow(id).copy(mergedBelow = listOf(arcanum.engine.state.MergedPart(part.id, part.owner, part.definition, timestamp = 1)))
        assertEquals(1, flyingOn(id))
        assertEquals(listOf<Ability>(arcanum.api.Keyword.Flying), seen(id).copiable!!.gained, "ObjectView.copiable has them")
        alice.activates(mold, target = clay)
        allPass()
        assertEquals(1, flyingOn(token()), "the token has them")
    }

    private val skyPart = Cards.invent("Sky Part", "{0}", "Creature — Bird", "Flying", "1/1")
    private val widerMold = Cards.invent("Wider Mold", "{0}", "Artifact", "{0}: Create a token that's a copy of target permanent you control.")

    private fun Scenario.mergeOnto(onto: String, part: String) {
        val p = state.objOrThrow(card(part))
        val id = card(onto) as ObjectId.Card
        state.objects[id] = state.objOrThrow(id).copy(mergedBelow = listOf(arcanum.engine.state.MergedPart(p.id, p.owner, p.definition, timestamp = 1)))
    }

    // CR 702.140e, 709.5: a merged Room with its own card on top has its other part's abilities with both doors locked
    @Test
    fun aMergedRoomHasThePartsAbilityWithDoorsLocked() = scenario {
        alice.battlefield(room, skyPart)
        mergeOnto(room, skyPart)
        assertNull(seen(card(room)).characteristics!!.name)
        assertEquals(1, flyingOn(card(room)))
    }

    // CR 702.140e, 707.2: a copy of a merged Room has them too, doors locked: they are no door's
    @Test
    fun aCopyOfAMergedRoomHasThePartsAbilityWithDoorsLocked() = scenario {
        alice.battlefield(room, skyPart, blank)
        mergeOnto(room, skyPart)
        effect(alice, copyMechanic, on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        assertEquals(1, flyingOn(card(blank)))
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), seen(card(blank)).copiable!!.faces.map { it.name })
    }

    // CR 702.140e, 707.2, 709.5d: a token copy of a merged Room enters locked and has them
    @Test
    fun aTokenCopyOfAMergedRoomHasThePartsAbilityWithDoorsLocked() = scenario {
        alice.battlefield(room, skyPart, widerMold)
        mergeOnto(room, skyPart)
        alice.activates(widerMold, target = room)
        allPass()
        assertNull(seen(token()).characteristics!!.name)
        assertEquals(1, flyingOn(token()))
    }

    // CR 702.140e, 707.3, 110.5b: a token copy of a merged flipped flip card enters unflipped and has them
    @Test
    fun aTokenCopyOfAMergedFlippedFlipCardHasThePartsAbility() = scenario {
        alice.battlefield(novice, skyPart, widerMold)
        alice.hand(turningWord)
        mergeOnto(novice, skyPart)
        alice.castsWithoutCosts(turningWord, target = novice)
        resolve()
        assertEquals(1, flyingOn(card(novice)))
        alice.activates(widerMold, target = novice)
        allPass()
        assertEquals("Glass Novice", seen(token()).characteristics!!.name)
        assertEquals(1, flyingOn(token()))
    }

    // CR 702.140e, 707.9a: what a token gained in copying and what it merged with count once each, and go on to a copy once each
    @Test
    fun aTokenThatGainedAnAbilityAndMergesHasEachOnce() = scenario {
        val walker = Cards.invent("Soft Walker", "{0}", "Creature — Bird", "Vigilance", "1/1")
        alice.battlefield(room, walker, blank)
        val id = card(room) as ObjectId.Card
        val p = state.objOrThrow(card(walker))
        state.objects[id] = state.objOrThrow(id).copy(token = true, gainedInCopying = listOf(arcanum.api.Keyword.Flying),
            mergedBelow = listOf(arcanum.engine.state.MergedPart(p.id, p.owner, p.definition, timestamp = 1)))
        effect(alice, copyMechanic, on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        fun vigilance(of: ObjectId) = seen(of).characteristics!!.abilities.count { it.ability == arcanum.api.Keyword.Vigilance }
        for (of in listOf(id, card(blank))) {
            assertEquals(1, flyingOn(of))
            assertEquals(1, vigilance(of))
            assertEquals(listOf<Ability>(arcanum.api.Keyword.Flying, arcanum.api.Keyword.Vigilance), seen(of).copiable!!.gained)
        }
    }

    // CR 707.9a, 709.5, 602: an activated ability gained in copying can be activated with the doors locked, and resolves
    @Test
    fun anAddedActivatedAbilityWorksWithDoorsLocked() = scenario {
        val donor = Cards.definition(Cards.invent("Life Donor", "{0}", "Artifact", "{0}: You gain 5 life.")).faces.single().abilities.single()
        alice.battlefield(room, blank)
        effect(alice, Mechanic.CopyOf(Objects.Bound("original"), listOf(CopyException.AddAbilities(listOf(donor)))),
            on = listOf(blank), names = mapOf("original" to room), duration = Duration.Indefinitely)
        val before = state.player(alice.id).life
        alice.activates(blank)
        allPass()
        assertEquals(before + 5, state.player(alice.id).life)
    }

    // ---- What stays as it was

    // CR 707.2b, 722.2b: a permanent entering as a copy fixes the values, layout and faces with them
    @Test
    fun aPermanentEnteringAsACopyFixesLayoutAndFaces() = scenario {
        val clone = Cards.invent("Mimic Ooze", "{0}", "Creature — Ooze", "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
        alice.battlefield(seraph)
        alice.hand(clone)
        alice.casts(clone)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(seraph)
        allPass()
        val copy = state.battlefield.first { state.objOrThrow(it).definition?.faces?.first()?.name == clone }
        val fixed = state.effects.values.single { it.locked?.contains(copy) == true }
        assertEquals(Layout.Prepare, fixed.copiedValues!!.layout)
        assertEquals(listOf("Hushed Seraph", "Small Mending"), fixed.copiedValues!!.faces.map { it.name })
        assertNotNull(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().firstOrNull { it.source == copy })
    }

    // ---- Copies of spells

    // CR 707.10f, 707.10g: a copy of a double-faced permanent spell is double-faced, and so is the token it becomes
    @Test
    fun aCopyOfADoubleFacedPermanentSpellBecomesADoubleFacedToken() = scenario {
        val echo = Cards.invent("Echo Shrine", "{0}", "Enchantment", "Whenever you cast a creature spell, copy that spell.")
        alice.battlefield(echo, "Forest", "Forest")
        alice.hand(werewolf)
        alice.casts(werewolf)
        allPass()
        allPass()
        allPass()
        val token = token()
        assertEquals(Layout.Transform, state.objOrThrow(token).definition!!.layout)
        activateOnly(token)
        assertEquals("Pale Howler", seen(token).characteristics!!.name)
    }

    // CR 707.10f, 709.5b, 709.5d: a copy of a Room spell becomes a token with both halves, neither unlocked: it wasn't cast
    @Test
    fun aCopyOfARoomSpellBecomesATokenWithBothDoorsLocked() = scenario {
        val echo = Cards.invent("Echo Chapel", "{0}", "Enchantment", "Whenever you cast an enchantment spell, copy that spell.")
        alice.battlefield(echo, "Plains", "Plains")
        alice.hand(room)
        alice.casts(room, way = CastWay(face = 0))
        allPass()
        allPass()
        allPass()
        val token = state.objOrThrow(token())
        assertEquals(listOf("Lantern Foyer", "Quiet Pantry"), token.definition!!.faces.map { it.name })
        assertTrue(token.marks.isEmpty())
        assertEquals(listOf<ObjectMark>(ObjectMark.Unlocked(Door.Left)), state.objOrThrow(card(room, 0)).marks, "the half cast is unlocked on the card")
    }

    // CR 707.10f, g: the spell it copies having left the Stack, a copy still becomes a token with all its faces
    @Test
    fun aCopyOfASpellThatHasLeftTheStackKeepsAllFaces() = scenario {
        val echo = Cards.invent("Echo Shrine", "{0}", "Enchantment", "Whenever you cast a creature spell, copy that spell.")
        alice.battlefield(echo, "Forest", "Forest")
        alice.hand(werewolf)
        alice.casts(werewolf)
        allPass() // the trigger: the copy is on the Stack above the spell
        val original = state.stack.last { it is ObjectId.Card }
        val copy = state.stack.single { it is ObjectId.StackItem }
        assertEquals(Layout.Transform, (state.stackItems[copy]!!.kind as arcanum.engine.state.StackItemKind.SpellCopy).values!!.layout)
        change(Changes.move(state, listOf(original), Zone.Graveyard))
        allPass()
        assertEquals(Layout.Transform, state.objOrThrow(token()).definition!!.layout)
    }

    // CR 707.9b: an exception to the copy is on each half of a copied Room
    @Test
    fun anExceptionAppliesToEachHalfOfACopiedRoom() = scenario {
        alice.battlefield(room, blank)
        effect(alice, Mechanic.CopyOf(Objects.Bound("original"), listOf(CopyException.Types(add = listOf(CardType.Artifact)))),
            on = listOf(blank), names = mapOf("original" to room))
        val v = seen(card(blank))
        assertEquals(listOf(CardType.Enchantment, CardType.Artifact), v.characteristics!!.types)
        assertTrue(v.copiable!!.faces.all { CardType.Artifact in it.types })
    }
}
