package org.asyncttwiz.server

import java.net.InetAddress
import java.net.URI
import org.asyncttwiz.server.accounts.ResetMailSettings
import org.asyncttwiz.server.database.DatabaseSettings

/** Loopback service settings on port 0: the OS picks free ports when the service binds, so no other process can
 * take a port between choosing and binding it. Tests read the actual ports through [startBound]. */
object EphemeralPorts {
    val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    fun settings(database: DatabaseSettings, mail: ResetMailSettings? = null) =
        ServiceSettings(database, URI("https://synthetic.invalid"), loopback, 0, loopback, 0, mail)
}

/** Starts this service and returns [settings] carrying the ports it actually bound. A restart from the same
 * port-0 settings binds fresh ports, so each started service must use its own returned settings. */
fun SpringService.startBound(settings: ServiceSettings): ServiceSettings {
    start()
    return ServiceSettings(settings.database(), settings.publicBaseUrl(), settings.publicAddress(), publicPort(),
        settings.healthAddress(), healthPort(), settings.resetMail())
}
