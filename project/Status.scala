import sbt._
import Keys._

object Status
{
  def stampVersion = Command.command("stamp-version") { state =>
    Project.extract(state).append((version ~= stamp) :: Nil, state)
  }
  def stamp(v: String): String =
    if(v endsWith Snapshot)
      (v stripSuffix Snapshot) + "-" + timestampString(System.currentTimeMillis)
    else
      v
  def timestampString(time: Long): String =
  {
    val format = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
    format.format(new java.util.Date(time))
  }
  final val Snapshot = "-SNAPSHOT"
}
