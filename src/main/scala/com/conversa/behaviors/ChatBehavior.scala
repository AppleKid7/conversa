package com.conversa.behaviors

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
    case CreateConversation(
        replier: Replier[Either[ChatError, Unit]]
    )
    case JoinConversation(
        memberId: String,
        maxNumberOfMembers: Int,
        replier: Replier[Either[ChatError, Unit]]
    )
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
      case ChatCommand.CreateConversation(replier) =>
        repo.createConversation(conversationId)
          *> replier.reply(Right(()))
      case ChatCommand.JoinConversation(memberId, maxNumberOfMembers, replier) =>
        repo.conversationExists(conversationId).flatMap { conversationExists =>
          if (conversationExists) {
            repo.getConversationMembers(conversationId).flatMap { members =>
              if (members.contains(memberId))
                replier
                  .reply(Left(ChatError.AlreadyJoined("You've already joined this conversation")))
              else if (members.size >= maxNumberOfMembers)
                replier.reply(
                  Left(
                    ChatError.ChatFull("You can no longer join this Match!", maxNumberOfMembers)
                  )
                )
              else
                repo.addMemberToConversation(conversationId, memberId) *> replier.reply(Right(()))
            }
          } else
            replier.reply(
              Left(
                ChatError.InvalidConversationId(s"conversation $conversationId does not exist")
              )
            )
        }
      case ChatCommand.SendMessage(sender, content, replier) =>
        repo.getConversationMembers(conversationId) flatMap { members =>
          if (members.contains(sender)) {
            val timestamp = java.time.Instant.now.toEpochMilli.toDouble
            val json =
              s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
            repo.storeMessage(conversationId, json, timestamp) *> replier.reply(Right(json))
          } else
            replier.reply(
              Left(
                ChatError.InvalidConversationId(
                  s"user $sender does not belong in conversation $conversationId"
                )
              )
            )
        }
      case ChatCommand.GetMessages(replier) =>
        replier.replyStream(
          repo.getAllMessages(conversationId)
        )
      case ChatCommand.Terminate(p) => p.succeed(()) *> ZIO.interrupt
    }
}
