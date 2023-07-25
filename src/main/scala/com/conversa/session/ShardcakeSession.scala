package com.conversa.session

import com.conversa.behaviors.ChatBehavior
import com.conversa.behaviors.ChatBehavior.ChatCommand
import com.conversa.models.ChatError
import com.conversa.models.ConversationId
import com.conversa.models.Message
import com.devsisters.shardcake.Sharding
import zio.*
import zio.json.*
import zio.stream.*

case class ShardcakeSession(
    sharding: com.devsisters.shardcake.Sharding,
    messages: Ref[List[Message]],
    subscribers: Hub[List[Message]]
) extends Session {
  val conversationShard = sharding.messenger[ChatCommand](ChatBehavior.Conversation)

  override def createConversation: IO[ChatError, ConversationId] =
    for {
      conversationId <- Random.nextUUID
      // persist to db
    } yield conversationId.toString()

  override def sendMessage(
      conversationId: ConversationId,
      sender: String,
      content: String
  ): IO[ChatError, List[Message]] = {
    (for {
      timestamp <- ZIO.succeed(java.time.Instant.now.toEpochMilli.toDouble)
      res <- conversationShard
        .send[Either[ChatError, List[String]]](conversationId)(
          ChatCommand.SendMessage(sender, content, _)
        )
        .orDie
      rawMessages <- ZIO.fromEither(res)
      parsedMessages <- ZIO.foreachPar(rawMessages)(message =>
        ZIO.fromEither(message.fromJson[Message]).mapError(e => ChatError.InvalidJson(e))
      )
    } yield parsedMessages).tap(added => subscribers.publish(added))
  }

  override def conversationEvents(
      connectionId: ConversationId
  ): ZStream[Any, Nothing, List[Message]] =
    ZStream.scoped(subscribers.subscribe).flatMap(ZStream.fromQueue(_))

  override def getMessages(connectionId: ConversationId): ZStream[Any, Throwable, Message] =
    ZStream.unwrap(conversationShard.sendStream[String](connectionId)(ChatCommand.GetMessages(_)).map(messageStream =>
      messageStream.mapZIO(rawMessage =>
        rawMessage.fromJson[Message] match {
          case Right(message) => ZIO.succeed(message)
          case Left(value) => ZIO.fail(new Throwable(value))
        }
      )
    ))
}
object ShardcakeSession {
  def make(
      initial: List[Message]
  ) = ZLayer.scoped {
    for {
      sharding <- ZIO.service[Sharding]
      messages <- Ref.make(initial)
      subscribers <- Hub.unbounded[List[Message]]
      // redis <- ZIO.service[RedisCommands[Task, String, String]]
    } yield ShardcakeSession(sharding, messages, subscribers)
  }
}
