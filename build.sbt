ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

val http4sVersion = "0.23.30"
val circeVersion  = "0.14.10"

// ── Backend (JVM): http4s server, also serves the UI ───────────────
lazy val root = (project in file("."))
  .settings(
    name := "weather-app",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % http4sVersion,
      "org.http4s"    %% "http4s-ember-client" % http4sVersion,
      "org.http4s"    %% "http4s-circe"        % http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % http4sVersion,
      "io.circe"      %% "circe-generic"       % circeVersion,
      "io.circe"      %% "circe-parser"        % circeVersion,
      "ch.qos.logback" % "logback-classic"     % "1.5.12",
      "org.typelevel"  %% "munit-cats-effect"   % "2.0.0"  % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    // Run the server in its own JVM so Ctrl+C / resource cleanup work properly.
    Compile / run / fork := true,
    // Compile the Scala.js frontend and bundle its JS into the server's
    // classpath under /public, next to index.html.
    Compile / resourceGenerators += Def.task {
      val report = (frontend / Compile / fastLinkJS).value.data
      val outDir = (frontend / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      val dest   = (Compile / resourceManaged).value / "public"
      report.publicModules.toSeq.flatMap { m =>
        // main.js plus its source map (if emitted)
        val files = Seq(m.moduleFileName, m.moduleFileName + ".map").filter(f => (outDir / f).exists)
        files.map { f =>
          IO.copyFile(outDir / f, dest / f)
          dest / f
        }
      }
    }.taskValue
  )

// ── Frontend (Scala.js + Laminar) ──────────────────────────────────
lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "weather-app-frontend",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "com.raquo" %%% "laminar" % "17.2.1"
  )
