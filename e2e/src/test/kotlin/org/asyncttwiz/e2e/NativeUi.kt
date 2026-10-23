package org.asyncttwiz.e2e

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

/** Exact production accessibility semantics only: no coordinates, injection or test endpoints. */
internal object NativeSelectors {
    fun javaString(value: String): String = buildString {
        append('"')
        for (char in value) append(when (char) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (char.code < 32) "\\u%04x".format(char.code) else char.toString()
        })
        append('"')
    }

    fun xpathString(value: String): String = when {
        '\'' !in value -> "'$value'"
        '"' !in value -> "\"$value\""
        else -> "concat(" + value.split('\'').joinToString(",\"'\",") { "'$it'" } + ")"
    }

    fun selector(value: String, description: Boolean = false): String =
        "new UiSelector().${if (description) "description" else "text"}(${javaString(value)})"

    fun actionXPath(value: String): String =
        "//*[@text=${xpathString(value)}]/ancestor-or-self::*[@clickable='true'][1]"
}

internal class NativeUi(private val driver: AndroidDriver) {
    private val wait = WebDriverWait(driver, Duration.ofSeconds(45))
        .ignoring(StaleElementReferenceException::class.java)

    private fun visible(value: String, description: Boolean = false): WebElement? =
        driver.findElements(AppiumBy.androidUIAutomator(NativeSelectors.selector(value, description)))
            .lastOrNull { it.isDisplayed }

    fun text(value: String): WebElement = wait.until { visible(value) }
    fun named(value: String): WebElement = wait.until { visible(value, true) }

    fun field(value: String): WebElement = wait.until {
        val exact = NativeSelectors.xpathString(value)
        driver.findElements(By.xpath("//android.widget.EditText[@content-desc=$exact or .//*[@content-desc=$exact]]"))
            .singleOrNull { it.isDisplayed }
    }

    fun scroll(value: String, description: Boolean = false): WebElement = wait.until {
        visible(value, description) ?: run {
            val containers = driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().scrollable(true)"))
            for (index in containers.indices) {
                try {
                    driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true).instance($index))" +
                            ".setMaxSearchSwipes(8).scrollIntoView(${NativeSelectors.selector(value, description)})"))
                    visible(value, description)?.let { return@until it }
                } catch (_: NoSuchElementException) { /* Try another actual scroll container. */ }
            }
            null
        }
    }

    private fun action(value: String): WebElement? {
        val targets = driver.findElements(By.xpath(NativeSelectors.actionXPath(value)))
            .filter { it.isDisplayed }.distinct()
        check(targets.size <= 1) { "Ambiguous exact native action: $value (${targets.size} visible targets)" }
        return targets.singleOrNull()
    }

    fun button(value: String) {
        wait.until {
            // A visible heading with the same text is not the action. Search for
            // the actual clickable ancestor, including when it is below the fold.
            var target = action(value)
            if (target == null) {
                val containers = driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().scrollable(true)"))
                search@ for (container in containers) {
                    for (direction in listOf("down", "up")) {
                        for (swipe in 1..8) {
                            val more = driver.executeScript("mobile: scrollGesture", mapOf(
                                "elementId" to (container as RemoteWebElement).id, "direction" to direction, "percent" to 0.8))
                            target = action(value)
                            if (target != null) break@search
                            if (more != true) break
                        }
                    }
                }
            }
            target?.takeIf { it.isEnabled }
        }.click()
    }

    /** Escape/hideKeyboard can cancel Compose dialogs. IME Done closes only the keyboard. */
    fun hideKeyboard() {
        if (driver.isKeyboardShown) {
            driver.executeScript("mobile: performEditorAction", mapOf("action" to "done"))
            wait.until { !driver.isKeyboardShown }
        }
    }

    fun dismissDialog(description: String, title: String) {
        hideKeyboard()
        named(description).click()
        wait.until { visible(title) == null }
    }

    /** A retained restart may restore the table/detail itself, not necessarily its list. */
    fun reopen(name: String, destination: String, description: Boolean = true) {
        wait.until {
            if (visible(destination, description) != null) true
            else action(name)?.takeIf { it.isEnabled }?.let { it.click(); true } ?: false
        }
        if (description) named(destination) else text(destination)
    }
}
