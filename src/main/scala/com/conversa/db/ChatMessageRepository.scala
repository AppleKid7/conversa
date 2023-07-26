package com.conversa.db

import com.conversa.models.Message
import zio.*
import zio.stream.*

trait ChatMessageRepository {
  def getAllMessages(conversationId: String): ZStream[Any, Nothing, String]
  def storeMessage(conversationId: String, msg: String, timestamp: Double): Task[String]
}
object ChatMessageRepository {
  def getAllMessages(conversationId: String): ZStream[ChatMessageRepository, Nothing, String] = 
    ZStream.environmentWithStream[ChatMessageRepository](_.get.getAllMessages(conversationId))
  def storeMessage(conversationId: String, msg: String, timestamp: Double): RIO[ChatMessageRepository, String] =
    ZIO.environmentWithZIO[ChatMessageRepository](_.get.storeMessage(conversationId, msg, timestamp))
}