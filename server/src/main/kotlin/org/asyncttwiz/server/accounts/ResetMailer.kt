package org.asyncttwiz.server.accounts

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException

enum class MailResult { DELIVERED, TEMPORARY, PERMANENT }
fun interface ResetMailer { fun send(recipient: String, link: String): MailResult }

/** Angus SMTP; bounded sockets, TLS hostname verification, no library debug or secret-bearing errors. */
class SmtpResetMailer(private val settings: ResetMailSettings) : ResetMailer {
    override fun send(recipient: String, link: String): MailResult {
        val props = Properties().apply {
            setProperty("mail.smtp.connectiontimeout", "3000")
            setProperty("mail.smtp.timeout", "3000")
            setProperty("mail.smtp.writetimeout", "3000")
            setProperty("mail.smtp.quitwait", "false")
            setProperty("mail.smtp.localhost", "localhost")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.starttls.enable", (settings.tls == "starttls").toString())
            setProperty("mail.smtp.starttls.required", (settings.tls == "starttls").toString())
            setProperty("mail.smtp.ssl.enable", (settings.tls == "tls").toString())
            setProperty("mail.smtp.auth", (settings.username != null).toString())
        }
        try {
            val session = Session.getInstance(props)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.from))
                setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
                subject = "Reset your Async Tabletop Wizards password"
                setText("Open this link to choose a new password (valid for 30 minutes):\n\n$link\n\n" +
                    "If you did not request this email, ignore it. Your password has not changed.\n", "UTF-8")
                saveChanges()
            }
            session.getTransport("smtp").use { transport ->
                transport.connect(settings.host, settings.port, settings.username, settings.password)
                transport.sendMessage(message, message.allRecipients)
            }
            return MailResult.DELIVERED
        } catch (failure: MessagingException) {
            var next: Exception? = failure
            repeat(16) {
                val code = when (val item = next) {
                    is SMTPAddressFailedException -> item.returnCode
                    is SMTPSendFailedException -> item.returnCode
                    else -> 0
                }
                if (code in 500..599) return MailResult.PERMANENT
                next = (next as? MessagingException)?.nextException
            }
            return MailResult.TEMPORARY
        } catch (_: RuntimeException) { return MailResult.TEMPORARY }
    }
}
