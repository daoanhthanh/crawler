package vn.flinters

class ProgressBar(msg: String) extends Thread {

  private final val WIDTH = 60

  private var continue: Boolean = true

  override def run(): Unit = {
    val start = "\uD83C\uDF32" // 🌲
    val end = "\uD83C\uDFE0" // 🏠
    val cursor = "⛟"

    var x = 0
    while (continue) {
      draw(start, end, cursor, x)
      x += 1
      try Thread.sleep(60)
      catch {
        case e: Exception =>
          e.printStackTrace()
      }
    }

  }

  private def draw(startPattern: String, endPattern: String, cursor: String, index: Int): Unit = {
    val passedPath = "_" * ((index - 1) % WIDTH)
    val pathLeft = "_" * (WIDTH - passedPath.length - 1)
    val path = startPattern + passedPath + cursor + pathLeft + endPattern
    print(s"\r$msg: " + path)
  }

  def close(): Unit = {
    continue = false
    println("")
  }

}
