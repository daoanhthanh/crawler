import mill._
import mill.scalalib._

object crawl_data extends ScalaModule {

  def scalaVersion = "2.13.11"

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(
      ivy"org.scalameta::munit::0.7.29"
    )
  }
}
