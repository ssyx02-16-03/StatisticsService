package service

import akka.actor.{Props, ActorSystem}
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JValue, JArray}

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
    for(i <- 1 to 100)println("\n")
    println("Uppdaterad: "+getNow)
    println
    patientsPerTeam()
    println("\nKötider")
    println("Triage: " + timeToEvent("triage_wait_times.json")+" minuter")
    println("Läkare: " + timeToEvent("doctor_wait_times.json")+" minuter")

    //transformActor !  new OutgoingMessage((result \ "hits" \ "total").values.toString)
  }

  private def patientsPerTeam(): Unit = {
    val nakba = elastic.query(elastic.ONGOING_PATIENT_INDEX, "nakba_count.json")
    val nakki = elastic.query(elastic.ONGOING_PATIENT_INDEX, "nakki_count.json")
    val nakkk = elastic.query(elastic.ONGOING_PATIENT_INDEX, "nakkk_count.json")
    val nakme = elastic.query(elastic.ONGOING_PATIENT_INDEX, "nakme_count.json")
    val nakor = elastic.query(elastic.ONGOING_PATIENT_INDEX, "nakor_count.json")
    val total = elastic.query(elastic.ONGOING_PATIENT_INDEX, "total_count.json")

    println("Antal patienter per team: ")
    println("NAKBA: " +(elastic.getResult(nakba) \ "hits" \"total").values)
    println("NAKKI: " +(elastic.getResult(nakki) \ "hits" \"total").values)
    println("NAKKK: " +(elastic.getResult(nakkk) \ "hits" \"total").values)
    println("NAKME: " +(elastic.getResult(nakme) \ "hits" \"total").values)
    println("NAKOR: " +(elastic.getResult(nakor) \ "hits" \"total").values)
    println("TOTAL: " +(elastic.getResult(total) \ "hits" \"total").values)
  }

  private def timeToEvent(query:String): Long = {
    val fetch = elastic.query(elastic.ONGOING_PATIENT_INDEX, query)
    val events = (elastic.getResult(fetch) \ "hits" \ "hits").asInstanceOf[JArray]
    var times = List[String]()
    events.values.foreach {t =>
      times = times ::: elastic.jsonExtractor[List[String]](List("fields", "CareContactRegistrationTime"), t)
    }
    val now = getNow
    var time:Long = 0
    times.foreach{ t =>
      time += timeDifference(t.toDateTime.toDateTime(DateTimeZone.forID("Europe/Stockholm")), now)
    }
    time = time / times.size
    time.toDuration.getStandardMinutes
  }

  private def getNow = {
    DateTime.now(DateTimeZone.forID("Europe/Stockholm"))
  }

  /** calculates the elaspsed time between two DateTimes and returns it in milliseconds. If
    * the time is negative it returns 0. If one of the time points is None, it returns -1. */
  private def timeDifference(fromTime: DateTime, toTime: DateTime): Long ={
    try{
      (fromTime to toTime).toDurationMillis
    }catch{
      case e:IllegalAccessException  => 0 // return 0 if time is negative
      case e:Exception => -1 // usually illegal formatting
    }
  }
}
