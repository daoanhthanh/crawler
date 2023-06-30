package vn.flinters

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.util.ByteString
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import java.nio.file.Path
import java.time.{Duration, LocalDate, ZonedDateTime}
import scala.collection.immutable.ListMap
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Sorting

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("json-reader")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  private type OrgId = String
  private type DurationTime = String
  private lazy val orgIdMap = OrganizationIdMapping.get
  private lazy val regex = ".*ORGANIZA=\\d*=(\\w+)\\+.+bq$".r
  private lazy val parseOrgIdPattern = raw"\b[a-fA-F0-9]{8}\b".r
  private val jsonFile = "session_endpoints.json"
  private val jsonSource = scala.io.Source.fromFile(jsonFile)
  private val ws = new StandaloneAhcWSClient(new DefaultAsyncHttpClient())
  private val jsonString: String = try {
    jsonSource.getLines().mkString("\n")
  } finally {
    jsonSource.close()
  }

  private val sessionEndpoints: Seq[String] = Json.parse(jsonString).as[Seq[String]]
  private val endpointSource = Source(sessionEndpoints)
  private val fetchAttemptFlow = Flow.fromFunction[String, Future[JsArray]](endpoint => {
    println(endpoint)
    for {
      attemptId <- ws.url(endpoint).get().map(x => ((Json.parse(x.body) \ "lastAttempt").get \ "id").get)
      result <- ws.url(s"https://digdag-ee.pyxis-social.com/api/attempts/${attemptId.as[String]}/tasks")
        .get().map(x => {
        (Json.parse(x.body) \ "tasks").get.as[JsArray]
      })
    } yield result
  }
  )

  private val sink: Sink[MutableMap[OrgId, (LocalDate, DurationTime)], Future[IOResult]] = Flow[MutableMap[OrgId, (LocalDate, DurationTime)]].
    fold(MutableMap.empty[OrgId, MutableMap[LocalDate, DurationTime]])((acc, ele) => {

      ele.foreach(x => {
        val orgId = x._1
        if (acc.contains(orgId)) {
          acc(orgId) += x._2
        } else {
          acc += (x._1 -> MutableMap(x._2))
        }
      })
      acc
    })
    .map(map => {
      val sorted = map.map(x => {
        (x._1 -> ListMap(x._2.toSeq.sortBy(_._1)(Ordering.by(_.toEpochDay)): _*))
      })

      sorted
    })
    .map( x => {
      x.map { case (orgId, innerMap) =>
        s"$orgId, ${innerMap.map { case (_, duration) => duration}.mkString(",")}"
      }
    }.mkString("\n"))
    .map(ByteString(_))
    .toMat(FileIO.toPath(Path.of("result.csv")))(Keep.right)

  def sortStrings(strings: Array[String]): Array[String] = {
    Sorting.quickSort(strings)
    strings
  }

  private def filterOnlyBigQuerySession(jsArray: JsArray): MutableMap[OrgId, (LocalDate, DurationTime)] = {

    val init = MutableMap.empty[OrgId, (LocalDate, DurationTime)]

    jsArray.value.foldLeft(init)((acc, ele) => {
      val findOrg = regex.findFirstIn((ele \ "fullName").get.as[String])
      if (findOrg.isDefined) {
        val orgIdShort = parseOrgIdPattern.findFirstIn(findOrg.get).get
        val orgIdFull = orgIdMap.get(orgIdShort)
        val startTime = ZonedDateTime.parse((ele \ "startedAt").get.as[DurationTime])
        val endTime = ZonedDateTime.parse((ele \ "updatedAt").get.as[DurationTime])
        acc += (orgIdFull.getOrElse("ahihi") -> (startTime.toLocalDate, getDuration(startTime, endTime)))
      }
      acc
    })
  }

  private def getDuration(start: ZonedDateTime, end: ZonedDateTime): String = {
    val duration = Duration.between(start, end)
    val hours: Long = duration.toHours
    val minutes: Long = duration.toMinutesPart
    val seconds: Long = duration.toSecondsPart

    f"$hours%02d:$minutes%02d:$seconds%02d"
  }

  endpointSource.via(fetchAttemptFlow).mapAsync(6) { jsArrayFuture =>
    jsArrayFuture.map(filterOnlyBigQuerySession)
  }.runWith(sink)
    .andThen({
      case _ =>
        ws.close()
        system.terminate()
    })




}
