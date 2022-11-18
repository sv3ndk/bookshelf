import java.sql.Ref
val Http4sVersion = "0.23.16"
val CirceVersion = "0.14.3"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.4.4"
val RefinedVersion = "0.10.1"

lazy val root = (project in file("."))
  .settings(
    organization := "svend.playground",
    name := "bike-configurator",
    version := "0.0.0",
    scalaVersion := "2.13.10",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion % Runtime,
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-ember-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-refined" % CirceVersion,
      "eu.timepit" %% "refined" % RefinedVersion,
      "eu.timepit" %% "refined-cats" % RefinedVersion,
      "org.scalameta" %% "munit" % MunitVersion % Test,
      "org.scalameta" %% "munit-scalacheck" % MunitVersion % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
      "org.typelevel" %% "scalacheck-effect" % "1.0.4" % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4" % Test
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework"),
    // removing strict options obtained from plugin
    scalacOptions := scalacOptions.value.filter { opt =>
      opt != "-Xfatal-warnings" && !opt.startsWith("-Wunused")
    }
  )
