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
    subscribers: Hub[Message]
) extends Session {
  val conversationShard = sharding.messenger[ChatCommand](ChatBehavior.Conversation)

  override def createConversation: IO[ChatError, String] =
    for {
      uuid <- Random.nextUUID
      conversationId = s"chat-${uuid.toString()}" // TODO newtype
      _ <- conversationShard
        .send[Either[ChatError, Unit]](conversationId)(
          ChatCommand.CreateConversation(conversationId, _)
        )
        .mapError(e => ChatError.NetworkReadError(e.getMessage()))
    } yield conversationId

  override def sendMessage(
      conversationId: ConversationId,
      sender: String,
      content: String
  ): IO[ChatError, Message] = {
    (for {
      res <- conversationShard
        .send[Either[ChatError, String]](conversationId)(
          ChatCommand.SendMessage(sender, content, _)
        )
        .orDie
      rawMessage <- ZIO.fromEither(res)
      message <- ZIO
        .fromEither(rawMessage.fromJson[Message])
        .mapError(e => ChatError.InvalidJson(e))
      _ <- messages.update(m => m.takeRight(25) :+ message)
    } yield message).tap(added => subscribers.publish(added))
  }

  override def conversationEvents(
      connectionId: ConversationId
  ): ZStream[Any, Nothing, Message] =
    ZStream.scoped(subscribers.subscribe).flatMap(ZStream.fromQueue(_))

  override def getMessages(connectionId: ConversationId): ZStream[Any, Throwable, Message] =
    ZStream.unwrap(
      conversationShard
        .sendStream[String](connectionId)(ChatCommand.GetMessages(_))
        .map(messageStream =>
          messageStream.mapZIO(rawMessage =>
            rawMessage.fromJson[Message] match {
              case Right(message) => ZIO.succeed(message)
              case Left(value) => ZIO.fail(new Throwable(value))
            }
          )
        )
    )
}
object ShardcakeSession {
  def make(
      initial: List[Message]
  ) = ZLayer.scoped {
    for {
      sharding <- ZIO.service[Sharding]
      messages <- Ref.make(initial)
      subscribers <- Hub.unbounded[Message]
    } yield ShardcakeSession(sharding, messages, subscribers)
  }
}
