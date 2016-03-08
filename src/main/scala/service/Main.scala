package service

import akka.actor.{Props, ActorSystem}
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s._
import org.json4s.JsonDSL._

object Main extends App {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // json4s needs this for something

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
    println("\n\nVäntetider genomsnitt")
    val triage = averageTimeToEvent("triage_wait_times.json")
    val doctor = averageTimeToEvent("doctor_wait_times.json")

    println("Triage: " + triage.time +" minuter, "+ triage.count+" patienter i kö")
    println("Läkare: " + doctor.time +" minuter, "+ doctor.count+" patienter i kö")

    //transformActor !  new OutgoingMessage((result \ "hits" \ "total").values.toString)
  }

  private def patientsPerTeam(): Unit = {
    println("Antal patienter per team: ")
    print("       total   blå     grön    gul     orange  röd")
    List("NAKBA", "NAKKI", "NAKKK", "NAKME", "NAKOR", "NAKM ").foreach(team => {
      print("\n"+team+"  ")
      List("", "blå", "grön", "gul", "orange", "röd").foreach(prio => {
        val search: JObject = {
          ("size" -> 100) ~
            ("query" ->
              ("multi_match" -> {
                ("query" -> (team +" "+prio)) ~
                  ("type" -> "cross_fields") ~
                  ("fields" -> List("Team", "Priority")) ~
                  ("operator" -> "and")
              }))
        }
        val searchString = write(search)
        val query = elastic.query(elastic.ONGOING_PATIENT_INDEX, searchString)
        val ans = elastic.getResult(query)
        print((ans \ "hits" \ "total").values+"       ")
      })
    })
  }

  private def averageTimeToEvent(query:String): Result = {
    val fetch = elastic.queryJsonFile(elastic.ONGOING_PATIENT_INDEX, query)
    val events = (elastic.getResult(fetch) \ "hits" \ "hits").asInstanceOf[JArray]
    var times = List[String]()

    events.values.foreach {t =>
      val time = elastic.jsonExtractor[List[String]](List("fields", "CareContactRegistrationTime"), t).head
      val localTime = List(time.toDateTime.toDateTime(DateTimeZone.forID("Europe/Stockholm")).toString())
      times = localTime ::: times
    }

    val now = getNow
    var time:Long = 0
    times.foreach{ t =>
      time += timeDifference(t.toDateTime, now)
    }
    val returnTime = times.size match {
      case 0 => -1
      case _ =>  time / times.size
    }
    new Result(returnTime.toDuration.getStandardMinutes, times.size)
  }

  class Result(timeIn:Long, countIn:Int) {
    val time = timeIn
    val count = countIn
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
