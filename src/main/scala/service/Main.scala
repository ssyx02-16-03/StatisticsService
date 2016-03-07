package service

import akka.actor.{Props, ActorSystem}
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

object Main extends App {
  // load configs from resources/application.conf
  val config = ConfigFactory.load()
  val sleepTime = config.getLong("sleepTime") * 1000000 //convert milliseconds to nanoseconds
  val elastic = new ElasticInterface

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, "StatisticsService")
  val transformActor = system.actorOf(Props[AMQInterface])
  transformActor ! "connect"
  loop()

  def loop(): Unit = {
    val lastTime = System.nanoTime()
    iteration()
    while(System.nanoTime() < lastTime + sleepTime) { }
    loop()
  }

  def iteration(): Unit = {
    val search = Queries.nakme_count
    val get = elastic.query(elastic.ONGOING_PATIENT_INDEX, search)
    val result = elastic.getResult(get)
    transformActor !  new OutgoingMessage((result \ "hits" \ "total").values.toString)
  }
}
