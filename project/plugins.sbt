scalaVersion := "2.12.11"

addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.9")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.4.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.8.0-RC1"
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.13")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
