import NativePackagerHelper._
import sbt.Keys.resourceDirectory

lazy val scala3Version = "3.3.1"
lazy val shardCakeVersion = "2.1.1" // "2.1.0+8-9094a08a-SNAPSHOT"
lazy val zioVersion = "2.0.18" // 2.0.12
lazy val zioAWSVersion = "6.20.149.1"
lazy val zioConfigVersion = "4.0.0-RC16"
lazy val zioHttpVersion = "3.0.0-RC2" // "3.0.0-RC3+12-93d8229b-SNAPSHOT" // "3.0.0-RC3"
lazy val zioJsonVersion = "0.5.0"
lazy val testContainersVersion = "1.19.0"
lazy val testContainersScalaVersion = "0.41.0"
lazy val ZioTestContainersVersion = "0.10.0"
lazy val jwtCoreVersion = "9.4.4"
lazy val sttpVersion = "3.8.15"

lazy val generalDeps = Seq(
  "com.softwaremill.sttp.client3" %% "zio-json" % sttpVersion,
  "dev.zio" %% "zio-aws-cognitosync" % zioAWSVersion,
  "dev.zio" %% "zio-aws-cognitoidentityprovider" % zioAWSVersion,
  "dev.zio" %% "zio-aws-core" % zioAWSVersion,
  "dev.zio" %% "zio-aws-dynamodb" % zioAWSVersion,
  "dev.zio" %% "zio-aws-netty" % zioAWSVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-test-junit" % zioVersion,
  "dev.zio" %% "zio-json" % zioJsonVersion,
  "dev.zio" %% "zio-config" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio" % zioVersion,
  "com.github.jwt-scala" % "jwt-core_3" % jwtCoreVersion,
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-http" % zioHttpVersion,
)

lazy val shardcakeDeps = Seq(
  "com.devsisters" % "shardcake-core_3" % shardCakeVersion,
  "com.devsisters" %% "shardcake-manager" % shardCakeVersion,
  "com.devsisters" %% "shardcake-storage-redis" % shardCakeVersion,
  "com.devsisters" %% "shardcake-protocol-grpc" % shardCakeVersion,
  "com.devsisters" %% "shardcake-serialization-kryo" % shardCakeVersion
)

lazy val testingDeps = Seq(
  "com.dimafeng" %% "testcontainers-scala-core" % testContainersScalaVersion % Test,
  "com.dimafeng" %% "testcontainers-scala-localstack-v2" % testContainersScalaVersion % Test,
  // "org.testcontainers" % "testcontainers" % testContainersVersion % Test,
  // "io.github.scottweaver" %% "zio-2-0-testcontainers-postgresql" % ZioTestContainersVersion % Test,
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "Conversa",
    organization := "com.conversa",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq(
      "-Xmax-inlines",
      "64",
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:higherKinds",
      "-language:existentials",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:postfixOps",
      "-explain-types",
      "-Ykind-projector",
    ),
    Test / unmanagedClasspath += baseDirectory.value / "resources",
    Test / fork := true,
    Test / javaOptions += "--add-opens=java.base/java.util=ALL-UNNAMED",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Sonatype OSS Snapshots Custom" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      generalDeps,
      shardcakeDeps,
      testingDeps
    ).flatten,
  )
