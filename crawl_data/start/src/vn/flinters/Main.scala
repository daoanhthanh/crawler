package vn.flinters

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Path
import java.time.{Duration, LocalDate, ZonedDateTime}
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("json-reader")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  private type OrgId = String
  private type DurationTime = String
  private lazy val orgIdMap = OrganizationIdMapping.get


  // importance parts
  private lazy val regex = ".*ORGANIZA=\\d*=(\\w+)\\+main$".r
  private lazy val parseOrgIdPattern = raw"\b[a-fA-F0-9]{8}\b".r




  private val jsonFile = "session_endpoints.json"
  private val jsonSource = scala.io.Source.fromFile(jsonFile)
  private val ws = new StandaloneAhcWSClient(new DefaultAsyncHttpClient())
  private val jsonString: String = try {
    jsonSource.getLines().mkString("\n")
  } finally {
    jsonSource.close()
  }

  private val headerDates: mutable.Set[LocalDate] = scala.collection.mutable.Set.empty


  val progressBar = new ProgressBar("Crawling data")
  progressBar.start()


  private lazy val sink: Sink[MutableMap[OrgId, (LocalDate, DurationTime)], Future[IOResult]] = Flow[MutableMap[OrgId, (LocalDate, DurationTime)]].
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
    .map(x => {
      x.map { case (orgId, innerMap) =>
        s"$orgId,${innerMap.map { case (date, duration) => duration }.mkString(",")}"
      }
    }.mkString("\n"))
    .map(ByteString(_))
    .toMat(FileIO.toPath(Path.of(fileResultName)))(Keep.right)

  val fileResultName = args.headOption.getOrElse("result").concat(".csv")

  val startProcessTime = System.currentTimeMillis()
  private val sessionEndpoints: Seq[String] = Json.parse(jsonString).as[Seq[String]]

  private val endpointSource = Source(sessionEndpoints)
  private val fetchAttemptFlow = Flow.fromFunction[String, Future[JsArray]](endpoint => {
    for {
      attemptId <- ws.url(endpoint).get().map(x => ((Json.parse(x.body) \ "lastAttempt").get \ "id").get)
      result <- ws.url(s"https://digdag-ee.pyxis-social.com/api/attempts/${attemptId.as[String]}/tasks")
        .get().map(x => {
        (Json.parse(x.body) \ "tasks").get.as[JsArray]
      })
    } yield result
  }
  )

  private def filterOnlyBigQuerySession(jsArray: JsArray): MutableMap[OrgId, (LocalDate, DurationTime)] = {

    val init = MutableMap.empty[OrgId, (LocalDate, DurationTime)]

    jsArray.value.foldLeft(init)((acc, ele) => {
      val findOrg = regex.findFirstIn((ele \ "fullName").get.as[String])
      if (findOrg.isDefined) {
        val orgIdShort = parseOrgIdPattern.findFirstIn(findOrg.get).get
        val orgIdFull = orgIdMap.get(orgIdShort)
        val startTime = ZonedDateTime.parse((ele \ "startedAt").get.as[DurationTime])
        val endTime = ZonedDateTime.parse((ele \ "updatedAt").get.as[DurationTime])
        headerDates += startTime.toLocalDate
        acc += (orgIdFull.getOrElse(orgIdShort) -> (startTime.toLocalDate, getDuration(startTime, endTime)))
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

  endpointSource.via(fetchAttemptFlow).mapAsync(16) { jsArrayFuture =>
    jsArrayFuture.map(filterOnlyBigQuerySession)
  }.runWith(sink)
    .onComplete{
      case _ =>
        val endProcessTime = System.currentTimeMillis()
        println(s"Total time: ${(endProcessTime - startProcessTime) / 1000}s")
        prependLineToFile()
        progressBar.close()
        ws.close()
        system.terminate()
    }


  private def prependLineToFile(): Unit = {

    val line = s"org_id, ${headerDates.toList.sorted(Ordering.by[LocalDate, Long](_.toEpochDay)).mkString(",")}"
    val file = new java.io.File(fileResultName)
    val tempFilePath = fileResultName + ".tmp"

    val reader = scala.io.Source.fromFile(file)
    val lines = reader.getLines().toList
    reader.close()

    val writer = new BufferedWriter(new FileWriter(tempFilePath))
    writer.write(line)
    writer.newLine()

    lines.foreach { existingLine =>
      writer.write(existingLine)
      writer.newLine()
    }

    writer.close()

    val tempFile = new java.io.File(tempFilePath)
    tempFile.renameTo(file)
  }


}
