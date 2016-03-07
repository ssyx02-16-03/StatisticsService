package service

import com.typesafe.config.ConfigFactory
import org.json4s.JValue
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import wabisabi.Client
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ElasticInterface {
  val PATIENT_TYPE = "patient_type"
  val ONGOING_PATIENT_INDEX= "on_going_patient_index"
  val FINISHED_PATIENT_INDEX = "finished_patient_index" // patients go here when they are removed

  // load configs from resources/application.conf
  val config = ConfigFactory.load()
  val ip = config.getString("elasticsearch.ip")
  val port = config.getString("elasticsearch.port")
  val client = new Client(s"http://$ip:$port") // creates a wabisabi client for communication with elasticsearch

  def query(index: String, query: String): Future[String] = {
    val search = JSONReader.read(query)
    client.search(index, search).map(_.getResponseBody)
  }

  def getResult(result: Future[String]): JValue ={
    while(!result.isCompleted){} //wait until done
    parse(result.value.get.get)
    //TODO failed guards
  }

  def jsonExtractor[A](keys: List[String], json: Any): A = {
    if(keys.isEmpty){
      return json.asInstanceOf[A]
    }

    json match {
      case jVal:JValue =>          jsonExtractor(keys.tail, jVal \ keys.head)
      case mop:Map[String, Any] => jsonExtractor(keys.tail, mop.get(keys.head).get)
    }
  }
}
