import mill._
import mill.scalalib._

object crawl_data extends ScalaModule {

  def scalaVersion = "2.13.11"

  // run this to update dependencies: ./mill crawl_data.resolvedIvyDeps
  override def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-stream:2.8.2",
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-circe:2.12.0"
  )

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }
}
