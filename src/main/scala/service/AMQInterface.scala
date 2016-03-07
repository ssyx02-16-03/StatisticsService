package service

import akka.actor.{Actor, ActorRef}
import com.codemettle.reactivemq.ReActiveMQExtension
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model.{Topic, AMQMessage}
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory

class AMQInterface extends Actor {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // reading from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("topics.read")
  val writeTo = config.getString("topics.write")

  var theBus: Option[ActorRef] = None

  def receive = {
    case "connect" => {
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    }
    case ConnectionEstablished(request, c) => {
      println("connected:" + request)
      c ! ConsumeFromTopic(readFrom)
      println("cons:" + c)
      theBus = Some(c)
      println("bus:" + theBus)
    }
    case ConnectionFailed(request, reason) => {
      println("failed:" + reason)
    }
    case mess @ AMQMessage(body, prop, headers) => {

    }
    case m:OutgoingMessage => {
      println(m.message)
      sendToEvah(m.message)
    }
  }

  private def sendToEvah(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(writeTo), AMQMessage(json))}
  }

  private def getNow = {
    DateTime.now(DateTimeZone.forID("Europe/Stockholm"))
  }
}

