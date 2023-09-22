package com.conversa

import com.dimafeng.testcontainers.LocalStackV2Container
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import software.amazon.awssdk.regions.Region
import zio._
import zio.aws.core.config._
import zio.aws.netty.NettyHttpClient

import java.net.{InetAddress, URI}
import scala.collection.immutable.ArraySeq

object TestLocalstack {

  val awsConfig: ZLayer[Any, Throwable, CommonAwsConfig & AwsConfig] = {
    val config = ZLayer.scoped {
      for {
        localstack <- makeContainer(LocalStackV2Container.Def(
          tag = "2.2.0",
          // Services are loaded lazily in latest localstack so need to be specific.
          services = ArraySeq.unsafeWrapArray(Service.values())
        ))
        hostIpAddress <- ZIO.attempt(InetAddress.getByName(localstack.host).getHostAddress)
        // All services are found in the same port in latest locastack, so no service-specific mapping needed
        port <- ZIO.attempt(localstack.mappedPort(4566))
        uri <- ZIO.attempt(new URI(s"http://${hostIpAddress}:${port}"))
      } yield CommonAwsConfig(
        region = Some(Region.US_EAST_1),
        endpointOverride = Some(uri),
        credentialsProvider = localstack.staticCredentialsProvider,
        commonClientConfig = None
      )
    }
    (NettyHttpClient.default ++ config) >>> AwsConfig.configured().passthrough
  }

  private def makeContainer(settings: LocalStackV2Container.Def): ZIO[Scope, Nothing, LocalStackV2Container] =
    ZIO.acquireRelease(ZIO.attempt(settings.start()).orDie)(container => ZIO.attempt(container.stop()).ignoreLogged)

}
