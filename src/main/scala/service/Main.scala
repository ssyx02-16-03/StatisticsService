package service

import akka.actor.{Props, ActorSystem}
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.JsonSerializer.None
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s.ext._
import org.json4s._
import org.json4s.JsonDSL._
import com.github.nscala_time.time.Imports._
import org.json4s.native.Serialization.{read, write}
import scala.concurrent.Future
import scala.collection.convert.Wrappers.JMapWrapper

object Main extends App {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // json4s serialization

  // load configs from resources/application.conf
  val config = ConfigFactory.load()
  val sleepTime = config.getLong("sleepTime") * 1000000 //convert milliseconds to nanoseconds

  val elastic = new ElasticInterface

  // activeMQ-things
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, "StatisticsService")
  val transformActor = system.actorOf(Props[AMQInterface])
  transformActor ! "connect"

  loop() //start looping

  /** Handles frame timing and calls the iteration() method */
  def loop(): Unit = {
    val lastTime = System.nanoTime() // takes timestamp
    iteration() // runs the program once
    while(System.nanoTime() < lastTime + sleepTime) { } // wait until 'sleepTime' milliseconds has passed since the last loop
    loop() // reloop
  }

  /** Runs the actual program */
  def iteration(): Unit = {
    println("\nUppdaterad: "+getNow+"\n")

    patientsPerTeam()
    currentWaitTimes()
    historicPatientCounter()
  }

  private def currentWaitTimes(): Unit ={
    println("\n\nVäntetider genomsnitt")
    val triage = timeExisted("triage_wait_times.json")
    val doctor = timeExisted("doctor_wait_times.json")
    println("Triage: " + triage.time +" minuter, "+ triage.count+" patienter i kö")
    println("Läkare: " + doctor.time +" minuter, "+ doctor.count+" patienter i kö")
    println
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
            ("multi_match" ->
              ("query" -> ( team + " " + prio )) ~
              ("type" -> "cross_fields") ~
              ("fields" -> List( "Team", "Priority" )) ~
              ("operator" -> "and")
            )
          )
        }
        val searchString = write(search)
        val query = elastic.query(elastic.ONGOING_PATIENT_INDEX, searchString)
        val ans = elastic.getResult(query)
        print((ans \ "hits" \ "total").values+"       ")
      })
    })
  }

  private def historicPatientCounter(): Unit ={
    val interval = 5 // time between data points, in minutes
    val samples = 48 // number of sampling points
    val patients:JValue = getPatientsPresentOverTime(interval, samples)
    val patientList = patients.asInstanceOf[JArray]

    println("Number of patients present at different times, interval="+interval+", samples="+samples )
    for(m <- 0 to samples){
      print((patientList(m) \ "hits" \ "total").values + " ")
    }
    println
  }

  /** Performs a query to the database and calculates the average time spent since the patients "CareContactRegistrationTime"
    * @param query name of the json file containing the elasticsearch query. Query must use (at least) "fields":["CareContactRegistrationtime"]
    * @return the average time the fetched patients have been waiting
    */
  private def timeExisted(query:String): Result = {
    val fetch = elastic.queryJsonFile(elastic.ONGOING_PATIENT_INDEX, query)
    val events = (elastic.getResult(fetch) \ "hits" \ "hits").asInstanceOf[JArray] // extract results and put into JArray
    var times = List[String]()

    // Put all CareContactREgistrationTimes into a List[String]
    events.values.foreach { t => // foreach received patient:
      // fetch "CareContactRegistrationTime" from patient:
      val time = elastic.jsonExtractor[List[String]](List("fields", "CareContactRegistrationTime"), t).head
      // try to fix any timezone related problems:
      val localTime = List(time.toDateTime.toDateTime(DateTimeZone.forID("Europe/Stockholm")).toString())
      times = localTime ::: times
    }
    val now = getNow // take timestamp to avoid strangeness

    // calculate sum of time in 'times'
    var time:Long = 0
    times.foreach{ t =>
      time += timeDifference(t.toDateTime, now)
    }
    val returnTime = times.size match {
      case 0 => -1 // avoid division by zero
      case _ =>  time / times.size // return average
    }
    new Result(returnTime.toDuration.getStandardMinutes, times.size)
  }

  /** Auxilliary class for averageTimeToEvent */
  class Result(timeIn:Long, countIn:Int) {
    val time = timeIn
    val count = countIn
    val timestamp = getNow
    override def toString: String = {
      "time: "+time+" count: "+count+" timestamp: "+timestamp
    }
  }

  /** current time, timezoned */
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

  /** Repeatedly calls getPatientsPresentAtTime and returns the results in a List. The first object will be
    * (interval * samples) minutes in the past and the last element will be the present situation. Calling this with
    * large values (>~1000) of samples will be VERY slow.
    * @param interval the time between samples
    * @param samples the number of samples
    * @return List of length [samples] where each element is a 'snapshot' of the database at a point in time
    */
  private def getPatientsPresentOverTime(interval:Int, samples:Int): List[JValue] ={
    val now = getNow
    var patients:List[JValue] = List()

    for(t <- 0 to samples){
      val time = now.minusMinutes(t*interval)
      val search = getPatientsPresentAtTime(time, time, List("CareContactId"))
      patients = List(elastic.getResult(search))::: patients
    }
    patients
  }

  /** Returns a snapshot from the database containing all the patients that were present (at any point) between the two
    * input timestamps.
    * @param from the lower time bound
    * @param to the upper time bound
    * @param fields the "fields":["stuff_i_want"] field.
    * @return List of patients
    */
  private def getPatientsPresentAtTime(from:DateTime, to: DateTime, fields: List[String]): Future[String] = {
    val query = write(Map(
      "size" -> 10000,
      "fields" -> fields,
      "query" ->
        ("filtered" ->
          ("filter" ->
            ("or" -> List(
              Map("bool" ->
                ("must" -> List(
                  Map("range" ->
                    ("CareContactRegistrationTime" ->
                      ("lte" -> to.toString())
                    )
                  ),
                  Map("range" ->
                    ("RemovedTime" ->
                      ("gte" -> from.toString())
                    )
                  )
                ))
              ),
              Map("bool" ->
                ("must" -> List(
                  Map("range" ->
                    ("CareContactRegistrationTime" ->
                      ("lte" -> to.toString())
                      )
                  ),
                  Map("match" ->
                    ("_index" -> "on_going_patient_index")
                  )
                ))
              )
            ))
          )
        )
    ))
    elastic.query("*", query)
  }
}
