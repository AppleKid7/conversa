package com.conversa.behaviors

import com.conversa.config.ChatConfig
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
import zio.{Dequeue, Promise, RIO, Random, Task, ZIO}
import zio.stream.ZStream

object ChatBehavior {
  enum ChatCommand {
    case SendMessage(
        sender: String,
        content: String,
        replier: Replier[Either[ChatError, List[String]]]
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
  ): RIO[Sharding with RedisCommands[Task, String, String], Nothing] =
    ZIO.serviceWithZIO[RedisCommands[Task, String, String]](redis =>
      ZIO.logInfo(s"Started entity $entityId") *>
        messages.take.flatMap(handleMessage(entityId, redis, _)).forever
    )

  def handleMessage(
      conversationId: String,
      redis: RedisCommands[Task, String, String],
      message: ChatCommand
  ): RIO[Sharding, Unit] =
    message match {
      case ChatCommand.SendMessage(sender, content, replier) => {
        val timestamp = java.time.Instant.now.toEpochMilli.toDouble
        val json =
          s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
        redis.zAdd(
          s"conversation:$conversationId:messages",
          args = None,
          ScoreWithValue(Score(timestamp), json)
        ) *> redis
          .zRevRange(s"conversation:$conversationId:messages", 0, -1)
          .flatMap(msgs => replier.reply(Right(msgs)))
      }

      // {
      //   val timestamp = java.time.Instant.now.toEpochMilli.toDouble
      //   val json =
      //     s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
      //   redis.zAdd(
      //     s"conversation:$conversationId:messages",
      //     args = None,
      //     ScoreWithValue(Score(timestamp), json)
      //   ) *> redis
      //     .zRevRange(s"conversation:$conversationId:messages", 0, -1)
      //     .flatMap { msgs =>
      //       ZIO.foreach(msgs)(message =>
      //         ZIO.fromEither(message.fromJson[Message]).mapError(e => new Throwable(e.toString()))
      //       )
      //     }
      //     .flatMap(msgs => replier.reply(Right(msgs)))
      // }
      // (for {
      //   timestamp <- ZIO.succeed(java.time.Instant.now.toEpochMilli.toDouble)
      //   json =
      //     s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
      //   _ <- redis.zAdd(
      //     s"conversation:$conversationId:messages",
      //     args = None,
      //     ScoreWithValue(Score(timestamp), json)
      //   )
      //   rawMessages <- redis.zRevRange(s"conversation:$conversationId:messages", 0, -1)
      //   messageList <- ZIO.foreach(rawMessages)(message =>
      //     ZIO.fromEither(message.fromJson[Message]).mapError(e => new Throwable(e.toString()))
      //   )
      // } yield messageList).flatMap(msgs => replier.reply(Right(msgs)))

      //   redis.zRevRange(s"conversation:$conversationId:messages", 0, -1).flatMap { messages =>
      //     val timestamp = java.time.Instant.now.toEpochMilli.toDouble
      //     val json =
      //       s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
      //     val msg = Message(
      //       conversationId,
      //       timestamp,
      //       sender,
      //       content
      //     )
      //     // redis.zAdd(
      //     //   s"conversation:$conversationId:messages",
      //     //   args = None,
      //     //   ScoreWithValue(Score(timestamp), json)
      //     // ) *> replier.reply(Right(messages.map(_.fromJson[Message]) :+ msg))

      //     (for {
      //       _ <- redis.zAdd(
      //         s"conversation:$conversationId:messages",
      //         args = None,
      //         ScoreWithValue(Score(timestamp), json)
      //       )
      //       messageList <- ZIO.foreach(messages)(message => ZIO.fromEither(message.fromJson[Message]).mapError(e => new Throwable(e.toString())))
      //     } yield messageList).flatMap(msgs => replier.reply(Right(msgs :+ msg)))
      //     // redis.zAdd(
      //     //   s"conversation:$conversationId:messages",
      //     //   args = None,
      //     //   ScoreWithValue(Score(timestamp), json)
      //     // ) *> replier.reply(Right(List(msg)))
      //   }

      // //   val timestamp = java.time.Instant.now.toEpochMilli.toDouble
      // //   val json =
      // //     s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
      // //   val msg = Message(
      // //     conversationId,
      // //     timestamp,
      // //     sender,
      // //     content
      // //   )
      // //   replier.reply(Right(List(msg)))
      // // }
      case ChatCommand.GetMessages(replier) =>
        replier.replyStream(
          ZStream.fromIterableZIO(redis.zRevRange(s"conversation:$conversationId:messages", 0, -1).orDie)
        )
      case ChatCommand.Terminate(p) => p.succeed(()) *> ZIO.interrupt
    }

}
