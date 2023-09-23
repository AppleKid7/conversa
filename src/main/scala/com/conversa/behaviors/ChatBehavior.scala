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
import zio.*
import zio.config.*
import zio.json.*
import zio.stream.*

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
    case SendMessageStream(
        sender: String,
        content: String,
        replier: Replier[Either[ChatError, String]]
    )
    case CreateUser(
        userId: String,
        password: String,
        replier: Replier[Either[ChatError, Unit]]
    )
    case GetUser(
        userId: String,
        replier: Replier[Either[ChatError, String]]
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
        repo.entityExists(conversationId, "conversations").flatMap { conversationExists =>
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
      case ChatCommand.SendMessageStream(sender, content, replier) => {
        val members: ZStream[Any, Nothing, String] = repo.getMembersStream(conversationId)
        val filterMember: ZPipeline[Any, Nothing, String, String] =
          ZPipeline.filter[String](s => s == sender)
        val timestamp = java.time.Instant.now.toEpochMilli.toDouble
        val sendLogic: ZSink[Any, Nothing, String, Nothing, Either[ChatError, String]] =
          ZSink.collectAll[String].map { members =>
            if (members.contains(sender)) {
              val json =
                s"""{"conversationId": "$conversationId", "timestamp": $timestamp, "sender": "$sender", "content": "$content"}"""
              Right(json)
            } else
              Left(
                ChatError.InvalidConversationId(
                  s"user $sender does not belong in conversation $conversationId for members: $members"
                )
              )
          }
        val sendMessage: ZIO[Any, Nothing, Either[ChatError, String]] =
          members.via(filterMember).run(sendLogic)
        for {
          result <- sendMessage
          either <- ZIO.fromEither(result).orDieWith(e => new Throwable(e.message))
          _ <- repo.storeMessage(conversationId, either, timestamp)
          sendResult <- replier.reply(result)
        } yield sendResult
      }
      case ChatCommand.CreateUser(sender, password, replier) =>
        repo.entityExists(sender, "users").flatMap { userExists =>
          if (userExists)
            replier.reply(Left(ChatError.InvalidUserId(s"invalid username $sender")))
          else
            repo.createUser(sender, password) *> replier.reply(Right(()))
        }
      case ChatCommand.GetUser(sender, replier) =>
        repo.getUserPassword(sender).flatMap { password =>
          password match {
            case Some(password) =>
              replier.reply(Right(password))
            case None =>
              replier.reply(Left(ChatError.InvalidUserId(s"user $sender not found")))
          }
        }
      case ChatCommand.Terminate(p) => p.succeed(()) *> ZIO.interrupt
    }
}
