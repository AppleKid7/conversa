package com.conversa

import com.conversa.config.*
import com.devsisters.shardcake.*
import com.devsisters.shardcake.interfaces.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider

object ShardManagerApp extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider.fromResourcePath()
    )

  def run: Task[Nothing] =
    Server
      .run
      .provide(
        ZLayer.succeed(ManagerConfig.default),
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        redis,
        StorageRedis.live, // store data in Redis
        PodsHealth.local, // just ping a pod to see if it's alive
        GrpcPods.live, // use gRPC protocol
        ShardManager.live // Shard Manager logic
      )
}
