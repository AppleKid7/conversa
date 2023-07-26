package com.conversa.behaviors

import com.conversa.config.ChatConfig
import com.conversa.db.ChatMessageRepository
import com.conversa.models.ChatError
import com.conversa.models.ConversationId
import com.conversa.models.Message
import com.devsisters.shardcake.Messenger
import com.devsisters.shardcake.{EntityType, Replier, Sharding, StreamReplier}
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.Score
import dev.profunktor.redis4cats.effects.ScoreWithValue
import scala.collection.{mutable => scm}
import scala.util.{Failure, Success, Try}
import zio.config.*
import zio.json.*
import zio.stream.ZStream
import zio.{Dequeue, Promise, RIO, Random, Task, ZIO}

object ChatBehavior {
  enum ChatCommand {
    case SendMessage(
        sender: String,
        content: String,
        replier: Replier[Either[ChatError, String]]
    )
    case GetMessages(
        replier: StreamReplier[String]
    )
    case Terminate(p: Promise[Nothing, Unit])
  }

  object Conversation extends EntityType[ChatCommand]("conversation")

  def behavior(
      entityId: String,
      messages: Dequeue[ChatCommand]
  ): RIO[Sharding & RedisCommands[Task, String, String] & ChatMessageRepository, Nothing] =
    ZIO.serviceWithZIO[RedisCommands[Task, String, String]](redis =>
      ZIO.serviceWithZIO[ChatMessageRepository](repo =>
        ZIO.logInfo(s"Started entity $entityId") *>
          messages.take.flatMap(handleMessage(entityId, redis, repo, _)).forever
      )
    )

  def handleMessage(
      conversationId: String,
      redis: RedisCommands[Task, String, String],
      repo: ChatMessageRepository,
      message: ChatCommand
  ): RIO[Sharding, Unit] =
    message match {
      case ChatCommand.SendMessage(sender, content, replier) => {
        (for {
          conversation <- redis.zRevRange(s"conversation:$conversationId:participants", 0, -1)
          timestamp = java.time.Instant.now.toEpochMilli.toDouble
          json =
            s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
          result <- repo.storeMessage(conversationId, json, timestamp)
        } yield result).flatMap(msg => replier.reply(Right(msg)))
      }
      case ChatCommand.GetMessages(replier) =>
        replier.replyStream(
          repo.getAllMessages(conversationId)
        )
      case ChatCommand.Terminate(p) => p.succeed(()) *> ZIO.interrupt
    }

}
