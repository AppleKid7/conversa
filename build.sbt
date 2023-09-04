import NativePackagerHelper._

lazy val scala3Version = "3.3.0"
lazy val shardCakeVersion = "2.1.0" // "2.0.6"
lazy val zioVersion = "2.0.15" // 2.0.12
lazy val zioAWSVersion = "6.20.103.1"
lazy val zioConfigVersion = "4.0.0-RC16"
lazy val zioCryptoVersion = "0.0.0+244-3ed85d66-SNAPSHOT"
lazy val zioHttpVersion = "3.0.0-RC2" // 3.0.0-RC1
lazy val zioJsonVersion = "0.5.0"
lazy val testContainersVersion = "0.40.17"

lazy val generalDeps = Seq(
  "dev.zio" %% "zio-aws-core" % zioAWSVersion,
  "dev.zio" %% "zio-aws-dynamodb" % zioAWSVersion,
  "dev.zio" %% "zio-aws-netty" % zioAWSVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-test-junit" % zioVersion,
  "dev.zio" %% "zio-json" % zioJsonVersion,
  "dev.zio" %% "zio-config" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-crypto" % zioCryptoVersion,
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

lazy val shardcakeDeps = Seq(
  "com.devsisters" % "shardcake-core_3" % shardCakeVersion,
  "com.devsisters" %% "shardcake-manager" % shardCakeVersion,
  "com.devsisters" %% "shardcake-storage-redis" % shardCakeVersion,
  "com.devsisters" %% "shardcake-protocol-grpc" % shardCakeVersion,
  "com.devsisters" %% "shardcake-serialization-kryo" % shardCakeVersion
)

lazy val testingDeps = Seq(
  "com.dimafeng" %% "testcontainers-scala-core" % testContainersVersion % Test,
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
    ).flatten
  )
