package com.conversa.db

import com.conversa.models.Message
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.Score
import dev.profunktor.redis4cats.effects.ScoreWithValue
import zio.*
import zio.json.*
import zio.stream.*

final case class RedisChatMessageRepository(redis: RedisCommands[Task, String, String])
    extends ChatMessageRepository {
  override def createConversation(conversationId: String): Task[Unit] =
    redis.lPush("conversation", conversationId).unit
    // redis.setBit(s"conversation:$conversationId:members", 0, 0).unit

  override def getConversation(conversationId: String): Task[List[String]] =
    redis.zRevRange(s"conversation:$conversationId:members", 0, -1)

  override def getAllMessages(conversationId: String): ZStream[Any, Nothing, String] =
    ZStream
      .fromIterableZIO(
        redis.zRevRange(s"conversation:$conversationId:messages", 0, -1).orDie
      )

  override def storeMessage(
      conversationId: String,
      msg: String,
      timestamp: Double
  ): Task[String] = {
    redis.zAdd(
      s"conversation:$conversationId:messages",
      args = None,
      ScoreWithValue(Score(timestamp), msg)
    ) *> ZIO.succeed(msg)
  }
}
object RedisChatMessageRepository {
  val live = ZLayer.scoped {
    for {
      redis <- ZIO.service[RedisCommands[Task, String, String]]
    } yield RedisChatMessageRepository(redis)
  }
}
