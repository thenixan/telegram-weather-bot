@file:JvmName("Main")

package com.seemsnerdy.weatherbot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.location
import com.github.kotlintelegrambot.entities.ChatAction
import io.github.cdimascio.dotenv.Dotenv
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.io.FileOutputStream
import java.net.URL


fun main() {
    val token = Dotenv.load()["BOT_TOKEN"] ?: throw IllegalArgumentException("No token provided")

    val bot = bot {
        this.token = token
        dispatch {
            command("start") { bot, update ->
                update.message?.let { message ->
                    bot.sendMessage(chatId = message.chat.id, text = "Send me your location")
                }
            }
            location { bot, update, location ->
                update.message?.let { message ->
                    bot.sendChatAction(chatId = message.chat.id, action = ChatAction.UPLOAD_DOCUMENT)

                    val result =
                        loadLocation(
                            lon = location.longitude,
                            lat = location.latitude,
                            prefix = message.chat.id.toString()
                        )
                    val animationFile =
                        makeAnimation(prefix = message.chat.id.toString(), outName = message.chat.id.toString())

                    animationFile
                        .takeIf { it.exists() }
                        ?.let { bot.sendDocument(chatId = message.chat.id, document = it) }

                    animationFile.delete()
                    result.forEach { it.delete() }
                }
            }
        }
    }
    bot.startPolling()
}

fun makeAnimation(prefix: String, outName: String): File {
    val file = File("$outName.mp4")

    file.delete()

    val imageMask = screenshotName(prefix, key = "*")

    val p = ProcessBuilder().command(
        "ffmpeg",
        "-framerate",
        "2",
        "-pattern_type",
        "glob",
        "-i",
        imageMask,
        "-c:v",
        "libx264",
        "-r",
        "30",
        "-pix_fmt",
        "yuv420p",
        file.absolutePath
    ).start()
    p.waitFor()
    val exitValue = p.waitFor()
    if (exitValue != 0) {
        file.delete()
    }
    return file
}

fun loadLocation(lat: Float, lon: Float, prefix: String): List<File> {
    val chromeOptions = ChromeOptions()
    chromeOptions.setExperimentalOption("mobileEmulation", mapOf("deviceName" to "iPad"))
    val driver: WebDriver = RemoteWebDriver(URL(Dotenv.load()["SELENIUM_URL"]), chromeOptions)
    driver.get("https://yandex.ru/pogoda/maps/nowcast?lat=$lat&lon=$lon&z=9")
    WebDriverWait(
        driver,
        30
    ).until { webDriver: WebDriver -> (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete" }

    WebDriverWait(driver, 30)
        .until { webDriver ->
            val r = (webDriver as JavascriptExecutor).executeScript("return window.jQuery.active") as Number
            return@until r.toInt() == 0
        }

    // ad
    driver.findElements(By.ByXPath(".//div[contains(@class, \"weather-maps__map-layer-controls-top-with-adv\")]/div[contains(@class, \"adv_type_top\")]"))
        .firstOrNull()?.let { element ->
            (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
        }
    // there would be rain in ... block
    driver.findElements(By.ByXPath("/html/body/div[2]/div/div[6]/div[2]")).firstOrNull()?.let { element ->
        (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
    }
    // map type button
    driver.findElements(By.ByXPath("/html/body/div[2]/div/div[5]")).firstOrNull()?.let { element ->
        (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
    }
    // location button
    driver.findElements(By.ByXPath("/html/body/div[2]/div/div[1]/ymaps/ymaps/ymaps/ymaps[4]/ymaps[4]/ymaps/div"))
        .firstOrNull()?.let { element ->
            (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
        }
    // plus-minus buttons
    driver.findElements(By.ByXPath("/html/body/div[2]/div/div[1]/ymaps/ymaps/ymaps/ymaps[4]/ymaps[3]/ymaps/div"))
        .firstOrNull()?.let { element ->
            (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
        }
    // detailed forecast button
    driver.findElements(By.ByXPath("/html/body/div[2]/div/div[4]/a"))
        .firstOrNull()?.let { element ->
            (driver as JavascriptExecutor).executeScript("arguments[0].style.visibility = 'hidden';", element)
        }


    val result = (0..12).map { it.toString().padStart(2, '0') }.map { frame ->
        WebDriverWait(driver, 30)
            .until { webDriver ->
                val r = (webDriver as JavascriptExecutor).executeScript("return window.jQuery.active") as Number
                return@until r.toInt() == 0
            }
        Thread.sleep(500)

        val screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
        val file = File(screenshotName(prefix = prefix, key = frame))
        FileOutputStream(file).use {
            it.write(screenshot)
            it.flush()
        }
        println("Frame $frame: ${file.absolutePath}")
        driver.findElement(By.ByXPath(".//html")).sendKeys(Keys.ARROW_RIGHT)
        file
    }
    driver.quit()
    return result
}

fun screenshotName(prefix: String, key: String): String = "$prefix-$key.png"