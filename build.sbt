import java.sql.Ref

val Http4sVersion = "0.23.16"
val CirceVersion = "0.14.3"
val RefinedVersion = "0.10.1"
val DoobieVersion = "1.0.0-RC1"
val HikariCPVersion = "5.0.1"
val LogbackVersion = "1.4.5"

val MunitVersion = "0.7.29"
val ScalacheckVersion = "1.0.4"
val TestcontainersScalaVersion = "0.40.11"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    organization := "svend.playground",
    name := "bookshelf",
    version := "0.0.0",
    scalaVersion := "2.13.10",
    Defaults.itSettings,
    IntegrationTest / fork := true,
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
      "org.tpolecat" %% "doobie-core" % DoobieVersion,
      "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
      "org.tpolecat" %% "doobie-refined" % DoobieVersion,
      "com.zaxxer" % "HikariCP" % HikariCPVersion,
      "org.scalameta" %% "munit" % MunitVersion % "it, test",
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % "it, test",
      "org.scalameta" %% "munit-scalacheck" % MunitVersion % Test,
      "org.typelevel" %% "scalacheck-effect" % ScalacheckVersion % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % TestcontainersScalaVersion % "it, test"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework"),
    // removing strict options obtained from plugin
    scalacOptions := scalacOptions.value.filter { opt =>
      opt != "-Xfatal-warnings" && !opt.startsWith("-Wunused")
    }
  )
