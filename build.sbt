name := "elsevier-test"

version := "0.1"

scalaVersion := "2.12.8"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math"  %% "kind-projector" % "0.9.9")
addCompilerPlugin("org.scalamacros" % "paradise"        % "2.1.0" cross CrossVersion.full)

libraryDependencies += "org.typelevel" %% "cats-effect" % "1.2.0" withSources () withJavadoc ()

val Http4sVersion = "0.20.0-M6"
val CirceVersion  = "0.11.0"

libraryDependencies += "org.http4s" %% "http4s-circe"        % Http4sVersion
libraryDependencies += "org.http4s" %% "http4s-dsl"          % Http4sVersion
libraryDependencies += "org.http4s" %% "http4s-client"       % Http4sVersion
libraryDependencies += "org.http4s" %% "http4s-blaze-client" % Http4sVersion

libraryDependencies += "io.circe" %% "circe-generic" % CirceVersion
libraryDependencies += "io.circe" %% "circe-optics"  % CirceVersion

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ypartial-unification"
)
