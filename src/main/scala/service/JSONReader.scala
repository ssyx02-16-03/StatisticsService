package service

import scala.io.Source
object JSONReader {
  val QUERIES_PATH = "src/main/scala/queries/"

  def read(filename: String): String = {
    Source.fromFile(QUERIES_PATH ++ filename).getLines.mkString
  }
}
