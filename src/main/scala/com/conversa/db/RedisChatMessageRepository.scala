package com.conversa.db

import com.conversa.models.Message
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.Score
import dev.profunktor.redis4cats.effects.ScoreWithValue
import zio.*
import zio.json.*
import zio.stream.*

final case class RedisChatMessageRepository(
    redis: RedisCommands[Task, String, String]
) extends ChatMessageRepository {
  override def createConversation(conversationId: String): Task[Unit] =
    redis.set(s"conversations:$conversationId", "")

  override def entityExists(entityId: String, entityType: String): Task[Boolean] =
    redis.exists(s"$entityType:$entityId")

  override def addMemberToConversation(conversationId: String, memberId: String): Task[Unit] =
    redis.lPush(s"$conversationId:members", memberId).unit

  override def getConversationMembers(conversationId: String): Task[Set[String]] =
    redis.lRange(s"$conversationId:members", 0, -1).map(_.toSet)

  override def getAllMessages(conversationId: String): ZStream[Any, Nothing, String] =
    ZStream.fromIterableZIO(
      redis.zRevRange(s"$conversationId:messages", 0, -1).orDie
    )

  override def storeMessage(
      conversationId: String,
      msg: String,
      timestamp: Double
  ): Task[String] = {
    redis.zAdd(
      s"$conversationId:messages",
      args = None,
      ScoreWithValue(Score(timestamp), msg)
    ) *> ZIO.succeed(msg)
  }

  override def getMembersStream(conversationId: String): ZStream[Any, Nothing, String] =
    ZStream.fromIterableZIO(
      redis.lRange(s"$conversationId:members", 0, -1).map(_.toSet).orDie
    )

  override def createUser(userId: String, encryptedPassword: String): Task[Unit] =
    redis.set(s"users:$userId", encryptedPassword)

  override def getUserPassword(userId: String): Task[Option[String]] =
    redis.get(s"users:$userId")
}
object RedisChatMessageRepository {
  val live = ZLayer.scoped {
    for {
      redis <- ZIO.service[RedisCommands[Task, String, String]]
    } yield RedisChatMessageRepository(redis)
  }
}
