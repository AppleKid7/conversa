package com.conversa.db

import com.conversa.models.Message
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.Score
import dev.profunktor.redis4cats.effects.ScoreWithValue
import zio.*
import zio.json.*
import zio.stream.*

final case class RedisRepository(redis: RedisCommands[Task, String, String])
    extends ChatMessageRepository {
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
object RedisRepository {
  val live = ZLayer.scoped {
    for {
      redis <- ZIO.service[RedisCommands[Task, String, String]]
    } yield RedisRepository(redis)
  }
}
