package com.conversa

import com.conversa.config.RedisUriConfig
import com.devsisters.shardcake.StorageRedis.Redis
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import zio.{Task, ZEnvironment, ZIO, ZLayer}
import zio.config.*
import zio.interop.catz.*

val redis: ZLayer[Any, Throwable, Redis] =
  ZLayer.scopedEnvironment {
    implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
    implicit val logger: Log[Task] = new Log[Task] {
      override def debug(msg: => String): Task[Unit] = ZIO.logDebug(msg)
      override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
      override def info(msg: => String): Task[Unit] = ZIO.logInfo(msg)
    }

    ZIO.config[RedisUriConfig](RedisUriConfig.config).flatMap { config =>
      (for {
        client <- RedisClient[Task].from(
          s"${config.uri}${config.username}:${config.password}@${config.host}:${config.port}"
        )
        commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
        pubSub <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
      } yield ZEnvironment(commands, pubSub)).toScopedZIO
    }
  }
