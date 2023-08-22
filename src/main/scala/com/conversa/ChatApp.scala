package com.conversa

import com.conversa.behaviors.ChatBehavior.*
import com.conversa.behaviors.ChatBehavior.ChatCommand.*
import com.conversa.config.ChatConfig
import com.conversa.config.ShardcakeConfig
import com.conversa.db.RedisChatMessageRepository
import com.conversa.models.ChatError
import com.conversa.models.Message
import com.conversa.session.Session
import com.conversa.session.ShardcakeSession
import com.devsisters.shardcake._
import com.devsisters.shardcake.interfaces.Serialization
import dev.profunktor.redis4cats.RedisCommands
import zio.config.typesafe.TypesafeConfigProvider
import zio.{Config => _, _}

object ChatApp extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider.fromResourcePath()
    )

  val config: ZLayer[Any, zio.Config.Error, com.devsisters.shardcake.Config] =
    ZLayer(ZIO.config[ShardcakeConfig](ShardcakeConfig.config).map { config =>
      com.devsisters.shardcake.Config.default.copy(shardingPort = config.port)
    }).debug

  val register =
    for {
      _ <- Sharding.registerEntity(Conversation, behavior, p => Some(Terminate(p)))
      _ <- Sharding.registerScoped
    } yield ()

  val program: ZIO[Session, Throwable, Unit] =
    for {
      session <- ZIO.service[Session]
      user1 <- Random.nextUUID.map(_.toString)
      user2 <- Random.nextUUID.map(_.toString)
      user3 <- Random.nextUUID.map(_.toString)
      user4 <- Random.nextUUID.map(_.toString)
      conversationId <- session.createConversation.mapError(e => new Throwable(e.message))
      _ <- session.joinConversation(conversationId, user1).mapError(e => new Throwable(e.message))
      _ <- session.joinConversation(conversationId, user2).mapError(e => new Throwable(e.message))
      _ <- session.joinConversation(conversationId, user3).mapError(e => new Throwable(e.message))
      _ <- session
        .sendMessageStream(conversationId, user1, "Hey, what's up")
        .mapError(e => new Throwable(e.message))
        .debug
      _ <- session
        .sendMessageStream(conversationId, user2, "Not much.")
        .mapError(e => new Throwable(e.message))
        .debug
      _ <- session
        .sendMessageStream(conversationId, user3, "Yeah, same.")
        .mapError(e => new Throwable(e.message))
        .debug
      _ <- session
        .sendMessageStream(conversationId, user4, "Hi, I'm error!")
        .fold(e => Console.printError(e.message), value => value)
        .debug
      _ <- session
        .getMessages(conversationId)
        .foreach(msg => Console.printLine(s"${msg.sender}: ${msg.content}"))
      _ <- ZIO.never
    } yield ()

  def run: Task[Unit] = ZIO.config[ChatConfig](ChatConfig.config).flatMap { chatConfig =>
    ZIO
      .scoped(register *> program)
      .provide(
        config,
        ZLayer.succeed(GrpcConfig.default),
        ZLayer.succeed(RedisConfig.default),
        redis,
        StorageRedis.live,
        KryoSerialization.live,
        ShardManagerClient.liveWithSttp,
        GrpcPods.live,
        Sharding.live,
        GrpcShardingService.live,
        ShardcakeSession.make(List.empty[Message], chatConfig.maxNumberOfMembers),
        RedisChatMessageRepository.live
      )
  }
}
