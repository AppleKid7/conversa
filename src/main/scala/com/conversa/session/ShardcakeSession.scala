package com.conversa.session

import com.conversa.behaviors.ChatBehavior
import com.conversa.behaviors.ChatBehavior.ChatCommand
import com.conversa.models.ChatError
import com.conversa.models.ConversationId
import com.conversa.models.Message
import com.conversa.models.UserId
import com.devsisters.shardcake.Sharding
import java.nio.charset.StandardCharsets.US_ASCII
import zio.*
import zio.crypto.hash.*
import zio.crypto.hash.HashAlgorithm.SHA512
import zio.json.*
import zio.stream.*

case class ShardcakeSession(
    sharding: com.devsisters.shardcake.Sharding,
    hash: Hash,
    maxNumberOfMembers: Int,
    messages: Ref[List[Message]],
    subscribers: Hub[Message]
) extends Session {
  lazy val conversationShard = sharding.messenger[ChatCommand](ChatBehavior.Conversation)

  override def createUser(conversationId: String, username: String, plainPassword: String) =
    for {
      digest <- ZIO.attempt(hash.hash[HashAlgorithm.SHA512](
        m = plainPassword,
        charset = US_ASCII
      )).orDie
      result <- conversationShard.send[Either[ChatError, Unit]](conversationId)(
        ChatCommand.CreateUser(username, digest.value, _)
      ).orDie
      either <- ZIO.fromEither(result).mapError(e => ChatError.NetworkReadError(e.message))
    } yield either

  override def createConversation: IO[ChatError, String] =
    for {
      uuid <- Random.nextUUID
      conversationId = s"conversations:chat-${uuid.toString()}" // TODO newtype
      _ <- conversationShard
        .send[Either[ChatError, Unit]](conversationId)(
          ChatCommand.CreateConversation(_)
        )
        .mapError(e => ChatError.NetworkReadError(e.getMessage()))
    } yield conversationId

  override def joinConversation(
      conversationId: ConversationId,
      memberId: UserId
  ): IO[ChatError, Unit] =
    for {
      res <- conversationShard
        .send[Either[ChatError, Unit]](conversationId)(
          ChatCommand.JoinConversation(memberId, maxNumberOfMembers, _)
        )
        .mapError(e => ChatError.NetworkReadError(e.getMessage()))
    } yield ()

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

  override def sendMessageStream(
      conversationId: ConversationId,
      sender: String,
      content: String
  ): IO[ChatError, Message] = {
    (for {
      res <- conversationShard
        .send[Either[ChatError, String]](conversationId)(
          ChatCommand.SendMessageStream(sender, content, _)
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

  override def checkPassword(connectionId: String, username: String, plainPassword: String): IO[ChatError, Boolean] =
    for {
      result <- conversationShard.send[Either[ChatError, String]](connectionId)(
        ChatCommand.GetUser(username, _)
      ).orDie
      digest <- ZIO.fromEither(result).mapError(e => ChatError.NetworkReadError(e.message))
      verified <- ZIO.attempt(hash.verify[HashAlgorithm.SHA512](
        m = plainPassword,
        digest = MessageDigest(digest),
        charset = US_ASCII
      )).orDie
    } yield verified
}
object ShardcakeSession {
  def make(
      initial: List[Message],
      maxNumberOfMembers: Int
  ) = ZLayer.scoped {
    for {
      sharding <- ZIO.service[Sharding]
      hash <- ZIO.service[Hash]
      messages <- Ref.make(initial)
      subscribers <- Hub.unbounded[Message]
    } yield ShardcakeSession(sharding, hash, maxNumberOfMembers, messages, subscribers)
  }
}
