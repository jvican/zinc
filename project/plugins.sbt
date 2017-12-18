addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.27")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.4")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12-rc5")
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0"
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.17")

val ReleaseEarly = RootProject(uri("git://github.com/scalacenter/sbt-release-early.git#5257935d2a0753fa09b372a5f4e3decf95788267"))
dependsOn(ProjectRef(ReleaseEarly.build, "sbt-release-early"))
