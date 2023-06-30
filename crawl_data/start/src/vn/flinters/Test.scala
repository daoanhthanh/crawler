//package vn.flinters
//
//
//import java.time.{Duration, ZonedDateTime}
//
//
//object Test extends App{
//
//  val end   = "2023-06-25T20:40:08Z"
//  val start = "2023-06-25T11:13:40Z"
//
//  val timeStart = ZonedDateTime.parse(start)
//
//  val timeEnd = ZonedDateTime.parse(end)
//
//  val duration = Duration.between(timeStart, timeEnd)
//
//  val hours: Long = duration.toHours
//  val minutes: Long = duration.toMinutesPart
//  val seconds: Long = duration.toSecondsPart
//
//
//  println(  f"$hours%02d:$minutes%02d:$seconds%02d")
//
//}
