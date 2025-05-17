package com.ewan.wallscheduler

import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/***
 * handles sending emails using amazon ses smtp credentials.
 * all details are read from environment variables.
 */
object EmailService {

    // ses username for smtp authentication (from environment variable)
    private val username = System.getenv("SES_USERNAME") ?: ""

    // ses password for smtp authentication (from environment variable)
    private val password = System.getenv("SES_PASSWORD") ?: ""

    // email address to send from (fallback to hardcoded default if not present)
    private val comingfrom = System.getenv("SES_FROM") ?: "ewan@room-scheduler.digital"

    /***
     * creates a configured session for sending emails via smtp.
     * sets required smtp flags and provides auth using ses credentials.
     */
    private fun session(): Session {
        val props = Properties().apply {
            // enable smtp authentication
            put("mail.smtp.auth", "true")

            // use tls encryption
            put("mail.smtp.starttls.enable", "true")

            // smtp endpoint for eu-west-2 region (london)
            put("mail.smtp.host", "email-smtp.eu-west-2.amazonaws.com")

            // smtp port used for tls
            put("mail.smtp.port", "587")
        }

        // create and return session with auth credentials
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })
    }

    /***
     * sends a plaintext email to the given recipient.
     * @param to the recipient’s email address
     * @param subject the email subject line
     * @param content the body of the email (plaintext)
     */
    fun send(to: String, subject: String, content: String) {
        try {
            // create the message using the smtp session
            val msg = MimeMessage(session()).apply {
                setFrom(InternetAddress(comingfrom, false))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setText(content)
            }

            // attempt to send the email
            Transport.send(msg)
        } catch (e: Exception) {
            // log any error that occurs
            println("Email send error: ${e.message}")
            e.printStackTrace()
        }
    }
}
