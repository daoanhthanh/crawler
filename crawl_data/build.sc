import mill._
import mill.scalalib._

object start extends ScalaModule {

  def scalaVersion = "2.13.11"

  override def forkArgs = Seq("-Xmx8g")

  // run this to update dependencies: ./mill crawl_data.resolvedIvyDeps
  override def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-stream:2.8.2",
    ivy"com.typesafe.play::play-json:2.9.4",
    ivy"com.typesafe.play::play-ahc-ws-standalone:2.1.10"
  )

  object test extends ScalaTests with TestModule.Munit {
    override def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }
}
