package integtests.com.conversa

import com.devsisters.shardcake.*
import com.devsisters.shardcake.ManagerConfig
import com.devsisters.shardcake.ShardManager
import com.devsisters.shardcake.StorageRedis.Redis
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.LocalStackV2Container
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import java.net.{InetAddress, URI}
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import scala.collection.immutable.ArraySeq
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import zio.*
import zio.Clock.ClockLive
import zio.aws.core.aspects.*
import zio.aws.core.config.*
import zio.aws.core.httpclient.HttpClient
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty.NettyHttpClient
import zio.interop.catz.*

object TestLocalstack extends Logging with Retries {
  val commonAwsConfig: ZLayer[Any, Throwable, CommonAwsConfig] = ZLayer.scoped {
    for {
      localstack <- makeContainer(
        LocalStackV2Container.Def(
          tag = "2.2.0",
          // Services are loaded lazily in latest localstack so need to be specific.
          services = ArraySeq.unsafeWrapArray(Service.values())
        )
      )
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

  val awsConfig: ZLayer[Any, Throwable, CommonAwsConfig & AwsConfig] = {
    (NettyHttpClient.default ++ commonAwsConfig) >>> AwsConfig.configured().passthrough
  }

  // val dynamoDb: ZLayer[AwsConfig, Throwable, DynamoDb] =
  //   DynamoDb.customized(
  //     _.credentialsProvider(
  //       StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "key"))
  //     ).region(Region.US_EAST_1).endpointOverride(new URI("http://localhost:4566"))
  //   ) @@ callLogging @@ callRetries

  val dynamoDb: ZLayer[AwsConfig, Throwable, DynamoDb] = DynamoDb.live @@ callLogging @@ callRetries

  // val dynamoDb: ZLayer[AwsConfig, Throwable, DynamoDb] = commonAwsConfig.flatMap { config =>
  //   DynamoDb.customized(
  //     _.credentialsProvider(
  //       StaticCredentialsProvider.create(config.get.credentialsProvider.resolveCredentials())
  //     ).region(config.get.region.get).endpointOverride(config.get.endpointOverride.get)
  //   )
  // }
  // DynamoDb.live @@ callLogging @@ callRetries

  private def makeContainer(
      settings: LocalStackV2Container.Def
  ): ZIO[Scope, Nothing, LocalStackV2Container] =
    ZIO.acquireRelease(ZIO.attempt(settings.start()).orDie)(container =>
      ZIO.attempt(container.stop()).ignoreLogged
    )
}

val container: ZLayer[Any, Nothing, GenericContainer] =
  ZLayer.scoped {
    ZIO.acquireRelease {
      ZIO.attemptBlocking {
        val container =
          new GenericContainer(dockerImage = "redis:latest", exposedPorts = Seq(6379))
        container.start()
        container
      }.orDie
    }(container => ZIO.attemptBlocking(container.stop()).orDie)
  }

val redis: ZLayer[GenericContainer, Throwable, Redis] =
  ZLayer.scopedEnvironment {
    implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
    implicit val logger: Log[Task] = new Log[Task] {
      override def debug(msg: => String): Task[Unit] = ZIO.unit
      override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
      override def info(msg: => String): Task[Unit] = ZIO.logDebug(msg)
    }

    ZIO
      .service[GenericContainer]
      .flatMap(container =>
        (for {
          client <- RedisClient[Task].from(
            s"redis://foobared@${container.host}:${container.mappedPort(container.exposedPorts.head)}"
          )
          commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
          pubSub <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
        } yield ZEnvironment(commands, pubSub)).toScopedZIO
      )
  }

val shardManagerServer: ZLayer[ShardManager with ManagerConfig, Throwable, Unit] =
  ZLayer(Server.run.forkDaemon *> ClockLive.sleep(3.seconds).unit)
