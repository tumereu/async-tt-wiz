package org.asyncttwiz.e2e

import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The web client in Chrome with every WebAssembly function it runs compiled by V8's optimizing compiler (owner report,
 * desktop playtest 2026-11-14: desktop Chrome showed "Aw, Snap! Out of memory" as a game's table opened, while Firefox ran
 * it). Chrome compiles a function again with that compiler once it is hot, and the card parsing as a table opens makes the
 * parser's sentence functions hot within seconds; a function too big for that compiler ends the whole page (V8's "Fatal
 * process out of memory: Zone"). Binaryen's inlining had made one 422 KB function of them. `--no-liftoff` has Chrome
 * use the optimizing compiler for every function from its first call, so the test doesn't wait for a function to get hot.
 */
class WebOptimizedCodeTest {
    // WEB-056, WEB-007: with every function optimized, Alice's browser loads, signs in and opens a started game's table,
    // where her device parses the game's cards (the engine's Web Worker) and she keeps her opening hand (the server takes it)
    @Test fun aTableOpensWithEveryFunctionOptimized() {
        val (alice, bob) = ServerSetup.fresh()
        val started = ServerSetup.prepare(alice, bob, TestDeck.thirds(), TestDeck.thirds(), name = "Optimized game").ready()
        E2eBrowser(chromeArgs = listOf("--js-flags=--no-liftoff")).use { web ->
            try {
                web.signIn(alice)
                val before = ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id)
                web.page.navigate("${E2eStack.origin}/games/${started.id}")
                web.waitFor("#main-button", 120_000.0)
                val deadline = System.nanoTime() + 90_000_000_000L
                // The game's cards parsed in the worker (its log reaches the page's console)
                while (web.console.none { "Card definitions:" in it && " ms, " in it } || !web.showsControl("Keep")) {
                    check(web.faults().isEmpty()) { "The page reported faults:\n${web.faults().joinToString("\n")}" }
                    check(System.nanoTime() < deadline) { "no parsed cards or no Keep; the main button: ${web.mainWords()}" }
                    Thread.sleep(200)
                }
                web.tap(web.control("Keep"))
                while (ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id) == before) {
                    check(web.faults().isEmpty()) { "The page reported faults:\n${web.faults().joinToString("\n")}" }
                    check(System.nanoTime() < deadline) { "Alice's keep never reached the server" }
                    Thread.sleep(300)
                }
                assertNotEquals(before, ServerSetup.head(alice, started.id).engine?.localHeads?.get(alice.id))
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebOptimizedCodeTest")
                throw failure
            }
        }
    }
}
