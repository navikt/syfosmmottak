package no.nav.paop.ws

import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.transport.http.HTTPConduit

fun configureBasicAuthFor(service: Any, username: String, password: String) =
        (ClientProxy.getClient(service).conduit as HTTPConduit).apply {
            authorization.userName = username
            authorization.password = password
        }