package org.asyncttwiz.android

import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Trigger timing (owner, 2026-11-22: "some table animations are slow", e.g. the wait after declaring an attack before the attack
 * triggers happen): gallery clips at real speed of how long the table takes from a player's action until its board settles,
 * with the default settings (Card move speed 1×, "Show plays and combat" 1.2 s, Quick). Alice's whole app plays them
 * ([LiveTableScreen]: her session, engine game and [TableScreen] on the simulated server, Bob's phone answering at once), so
 * each wait is the app's own on the clock: the plays shown and their flights (HIS-112, HIS-133, HIS-136, HIS-138), the target
 * arrows (STK-058, STK-127, STK-128, HIS-230), resolutions (TBL-150), the combat holds (HIS-113) and playback (HIS-021). No
 * network time, and no engine working time (the clock stands still while it works). Each clip's moments are written beside
 * the build's clip frames (build/outputs/clips/timing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The app's own monotonic clock (org.asyncttwiz.appui's nanoTime: the event chip's lifetime, HIS-077, and the change flashes',
// HIS-031) on the main looper's clock rather than the machine's, so each run draws the same frames. That clock runs about twice as
// fast as the frames under test, so the chip and the flashes go sooner than on a phone; the plays, arrows, holds and
// resolutions are timed on the frame clock (the Stack animation's clock is put on it, LiveTableScreen)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi", instrumentedPackages = ["org.asyncttwiz.appui"])
class TriggerTimingClipTest {
    @get:Rule val compose = createComposeRule()
    private var screen: LiveTableScreen? = null

    @After fun close() { screen?.close() }

    private fun open(kit: LiveTableKit) = LiveTableScreen(compose, kit).also { screen = it; it.open() }

    /** A main button press the test makes once it has shown [after] ms (a player's quick answer), during a clip. */
    private class Answer(val label: String, val after: Long) { var seenAt: Long? = null; var done = false }

    private fun LiveTableScreen.answer(answers: List<Answer>, at: Long) {
        val next = answers.firstOrNull { !it.done } ?: return
        if (!mainButton(next.label)) return
        val seen = next.seenAt ?: at.also { next.seenAt = it }
        if (at - seen >= next.after) { next.done = true; moments += "+$at ms  the test taps ${next.label}"; tapMain(next.label) }
    }

    /**
     * The clip [file] of [millis] (from [CLIP_LEAD_MILLIS] before the action), the table running on by itself; [act] does the
     * player's action at the lead, [during] runs before each frame after it with the time since the action. Its moments (what
     * the table held, when it changed, and the app log's lines) go to build/outputs/clips/timing.
     */
    private fun LiveTableScreen.clip(file: String, caption: String, millis: Long, act: () -> Unit, during: (Long) -> Unit = {}) {
        var last = ""
        origin = compose.mainClock.currentTime + CLIP_LEAD_MILLIS
        compose.captureClip("$SCREENSHOTS/clips/$file", caption, millis, advance = { advance(it) }) { at ->
            if (at > 0) world()
            if (at == CLIP_LEAD_MILLIS) act()
            if (at > CLIP_LEAD_MILLIS) during(at - CLIP_LEAD_MILLIS)
            compose.waitForIdle()
            val held = sample()
            if (held != last) { moments += "+${at - CLIP_LEAD_MILLIS} ms  table: $held"; last = held }
        }
        File("build/outputs/clips/timing").apply { mkdirs() }.resolve(file.substringAfterLast('/').removeSuffix(".webp") + ".txt")
            .writeText(moments.joinToString("\n") + "\n")
    }

    // HIS-113, HIS-112, HIS-133, HIS-136, STK-058, STK-127, HIS-230, TBL-150, TBL-205 (owner, 2026-11-22: trigger timing clips):
    // Alice attacks with an Ember Raider and a Spark Harrier, each with an attack trigger (the Harrier's at Bob, its one target)
    @Test fun yourAttackWithTwoAttackTriggers() {
        val s = open(TriggerTimingGames.raiderAndHarrier())
        s.tapOnField(Choices.RAIDER)
        s.run(600)
        s.tapOnField(Choices.HARRIER)
        s.run(1_000)
        val answers = listOf(Answer("OK", 1_000))
        s.clip("trigger-timing/your-attack.webp", CAPTION_YOUR_ATTACK, millis = 6_700, act = { s.tapMain("Attack (2)") },
            during = { s.answer(answers, it) })
    }

    // HIS-113, HIS-021, HIS-112, STK-058, STK-127, HIS-230, TBL-150, TBL-206 (owner, 2026-11-22): Bob attacks Alice with a Spark
    // Harrier, its attack trigger at her; her phone plays it back
    @Test fun theOtherPlayersAttackWithAnAttackTrigger() {
        val s = open(TriggerTimingGames.bobsHarrier())
        val kit = s.kit
        s.clip("trigger-timing/their-attack.webp", CAPTION_THEIR_ATTACK, millis = 5_400, act = {
            val harrier = kit.field(Choices.HARRIER, kit.other, kit.state(kit.f.b)).single()
            kit.bob.send(listOf(Input.DeclareAttackers(kit.other, listOf(Attack(harrier, TargetRef.Player(kit.me))))))
            kit.pump()
        })
    }

    // HIS-112, HIS-133, HIS-136, HIS-138, TBL-150 (owner, 2026-11-22): Alice casts a Kindle Herald with her Kindle Warden out: the
    // Herald's enters trigger makes a token, and the Herald and the token entering each trigger the Warden
    @Test fun yourCreatureWhoseTokenTriggersAnother() {
        val s = open(TriggerTimingGames.aliceWardenAndHerald())
        s.tapInHand(Choices.KINDLE_HERALD)
        s.run(1_000)
        val answers = listOf(Answer("OK", 1_000))
        s.clip("trigger-timing/your-token-chain.webp", CAPTION_YOUR_CHAIN, millis = 7_400, act = { s.tapInHand(Choices.KINDLE_HERALD) },
            during = { s.answer(answers, it) })
    }

    // HIS-112, HIS-021, HIS-133, HIS-136, HIS-138, TBL-150, TBL-206 (owner, 2026-11-22): Bob casts the same chain, his phone
    // ordering his triggers; Alice's phone plays it back
    @Test fun theOtherPlayersCreatureWhoseTokenTriggersAnother() {
        val s = open(TriggerTimingGames.bobWardenAndHerald())
        s.clip("trigger-timing/their-token-chain.webp", CAPTION_THEIR_CHAIN, millis = 8_500,
            act = { s.kit.cast(s.kit.bob, Choices.KINDLE_HERALD); s.kit.pump() })
    }

    // HIS-138, HIS-112, TBL-150, HIS-021 (owner, 2026-11-22): Alice's Ember Unmaking destroys Bob's four Ash Mourners, each with
    // "When this creature dies, you gain 1 life.": his four triggers show one after another, each repeat shorter
    @Test fun aBoardWipeWithFourDiesTriggers() {
        val s = open(TriggerTimingGames.unmakingAtMourners())
        s.tapInHand(Choices.UNMAKING)
        s.run(1_000)
        s.clip("trigger-timing/board-wipe.webp", CAPTION_WIPE, millis = 7_000, act = { s.tapInHand(Choices.UNMAKING) })
    }

    // HIS-112, HIS-133, HIS-136, STK-058, STK-127, HIS-230, TBL-150 (owner, 2026-11-22): Alice casts a Spark Lancer; its enters
    // trigger (its one target, Bob's Ash Pup, chosen by itself in Quick) shows in the middle, flies to the Stack, its arrow grows
    // to the Pup and is read, and it resolves, the Pup dying
    @Test fun aSingleTargetedTrigger() {
        val s = open(TriggerTimingGames.lancerAtPup())
        s.tapInHand(Choices.LANCER)
        s.run(1_000)
        s.clip("trigger-timing/targeted-trigger.webp", CAPTION_TARGETED, millis = 3_800, act = { s.tapInHand(Choices.LANCER) })
    }

    // Each caption: what the clip shows, how the test got there, and the waits it holds (as the app's log measured them)
    private companion object {
        const val CAPTION_YOUR_ATTACK = "Alice taps Attack (2) with her Ember Raider and Spark Harrier staged (the clip starts 0.3 s " +
            "before the tap) and her table runs on by itself to her second main phase, the test tapping OK on the triggers' order " +
            "1 s after it is asked: no hold on the attackers (the order asked as they are declared), both triggers fly in in 0.34 s, each shown " +
            "1.25 s and flying 0.25 s to the Stack, the Harrier's arrow to Bob grown and read in 0.65 s while the Raider's shows, " +
            "each resolution's effect about 0.35 s with the 0.33 s playback pause after it (Bob's passes resolve them), damage " +
            "held 1.2 s, the band closing in 0.35 s: about 6.1 s in all."
        const val CAPTION_THEIR_ATTACK = "Bob attacks Alice with his Spark Harrier (the clip starts 0.3 s before his attack reaches " +
            "her phone) and her phone plays it back to his second main phase: no hold on the attackers, his trigger flies in in " +
            "0.29 s, shown 1.25 s and flying 0.25 s to the Stack, its arrow to Alice grown and read in 0.65 s, its effect about " +
            "0.35 s with the 0.33 s playback pause, damage held 1.2 s, the band closing in 0.35 s: about 4.8 s in all."
        const val CAPTION_YOUR_CHAIN = "Alice casts a Kindle Herald with her Kindle Warden out (the test raised it with a tap; the " +
            "clip starts 0.3 s before the tap that casts it) and her table runs on until her move again, the test tapping OK on " +
            "the triggers' order 1 s after it is asked: the spell resolves at once, the Herald's and the Warden's triggers fly in " +
            "in 0.34 s and show 1.25 s each with 0.25 s flights, the Herald's resolves (its token flies out, about 0.35 s), the " +
            "token's Warden trigger shows as a repeat 0.82 s with a 0.16 s flight and resolves (about 0.35 s), then the first " +
            "Warden trigger resolves as a repeat (about 0.25 s): about 6.7 s in all."
        const val CAPTION_THEIR_CHAIN = "Bob casts a Kindle Herald with his Kindle Warden out, his phone ordering the triggers at " +
            "once (the clip starts 0.3 s before his cast reaches Alice's phone), played back on her phone until it settles: his " +
            "spell flies in in 0.29 s, shown 1.23 s and flying 0.25 s to the Stack, then resolves (about 0.35 s), his two " +
            "triggers fly in in 0.34 s and show 1.25 s each with 0.25 s flights, the token's Warden trigger shows as a repeat " +
            "0.82 s with a 0.16 s flight, each resolution about 0.35 s and the last, a repeat, about 0.25 s: about 7.8 s in all."
        const val CAPTION_WIPE = "Alice casts an Ember Unmaking (the test raised it with a tap; the clip starts 0.3 s before the " +
            "tap that casts it), destroying Bob's four Ash Mourners, and his four dies triggers play out on her phone: the wipe " +
            "resolves at once and a 0.33 s playback pause follows, the four fly in in 0.43 s, then show 1.25 s, 0.83 s, 0.56 s " +
            "and 0.37 s (each repeat 35% shorter) with flights of 0.25, 0.16, 0.11 and 0.1 s, then resolve one by one with their " +
            "effects at 100%, 65%, 43% and 43% of the time and the 0.33 s playback pause between Bob's events: about 6.3 s in all."
        const val CAPTION_TARGETED = "Alice casts a Spark Lancer (the test raised it with a tap; the clip starts 0.3 s before the " +
            "tap that casts it) and its enters trigger plays out until her move again: the spell resolves at once, the trigger " +
            "flies in in 0.29 s, shows 1.25 s and flies 0.25 s to the Stack, its arrow to Bob's Ash Pup grows and is read in " +
            "0.65 s, and it resolves, the Pup dying (about 0.4 s): about 3.1 s in all."
    }
}
