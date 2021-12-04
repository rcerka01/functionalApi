name := "functionalApi"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= {

  val Http4sVersion = "0.23.6"
  val CirceVersion = "0.14.1"
  val MunitVersion = "0.7.29"
  val LogbackVersion = "1.2.6"
  val MunitCatsEffectVersion = "1.0.6"

  Seq(
    "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion,
    "io.circe"   %% "circe-generic" % CirceVersion,
  )
}
