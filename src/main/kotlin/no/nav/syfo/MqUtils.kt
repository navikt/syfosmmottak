package no.nav.syfo

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import javax.jms.Session
import javax.jms.TextMessage

const val numListeners = 5

fun initMqConnectionFactory(env: Environment) = MQConnectionFactory().apply {
    hostName = env.mqHostname
    port = env.mqPort
    queueManager = env.mqQueueManagerName
    transportType = WMQConstants.WMQ_CM_CLIENT
    channel = env.mqChannelName
    ccsid = 1208
    setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, 1208)
}

fun initMqConnection(env: Environment) = initMqConnectionFactory(env)
        .createConnection(env.srvappserverUsername, env.srvappserverPassword)

fun exampleProducer() {
    val env = Environment()

    // .use is like try-with-resources in java, it closes the connection whenever the block is finished
    initMqConnection(env).use {
        val session = it.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val queue = session.createQueue(env.sampleQueueQueuename)

        val messageProducer = session.createProducer(queue)
        messageProducer.send(session.createTextMessage("This is a message"))
    }
}

fun exampleAsyncConsumer() {
    val env = Environment()

    initMqConnection(env).use {
        val session = it.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val queue = session.createQueue(env.sampleQueueQueuename)

        (1..numListeners).map {
            val messageConsumer = session.createConsumer(queue)
            messageConsumer.setMessageListener {
                if (it is TextMessage) {
                    println(it.text)
                }
            }
            messageConsumer
        }
    }
}

fun exampleSyncConsumer(applicationState: ApplicationState) {
    val env = Environment()
    initMqConnection(env).use {
        val session = it.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val queue = session.createQueue(env.sampleQueueQueuename)

        val listeners = (1..numListeners).map {
            launch {
                val messageConsumer = session.createConsumer(queue)
                try {
                    while (applicationState.running) {
                        val message = messageConsumer.receive()
                        if (message is TextMessage) {
                            println(message.text)
                        }
                    }
                } finally {
                    // Make sure we handle exiting the loop
                    applicationState.running = false
                }
            }
        }.toList()

        runBlocking {
            listeners.forEach { it.join() }
        }
    }

}
