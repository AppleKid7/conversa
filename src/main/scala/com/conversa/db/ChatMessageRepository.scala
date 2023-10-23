package com.conversa.db

import com.conversa.models.Message
import zio.*
import zio.stream.*

trait ChatMessageRepository {
  def createConversation(conversationId: String): Task[Unit]
  def entityExists(entityId: String, entityType: String): Task[Boolean]
  def getConversationMembers(conversationId: String): Task[Set[String]]
  def getMembersStream(conversationId: String): ZStream[Any, Nothing, String]
  def addMemberToConversation(conversationId: String, memberId: String): Task[Unit]
  def getAllMessages(conversationId: String): ZStream[Any, Nothing, String]
  def storeMessage(conversationId: String, msg: String, timestamp: Double): Task[String]
}
object ChatMessageRepository {
  def createConversation(conversationId: String): RIO[ChatMessageRepository, Unit] =
    ZIO.environmentWithZIO[ChatMessageRepository](_.get.createConversation(conversationId))
  def entityExists(entityId: String, entityType: String): RIO[ChatMessageRepository, Boolean] =
    ZIO.environmentWithZIO[ChatMessageRepository](_.get.entityExists(entityId, entityType))
  def addMemberToConversation(
      conversationId: String,
      memberId: String
  ): RIO[ChatMessageRepository, Unit] =
    ZIO.environmentWithZIO[ChatMessageRepository](
      _.get.addMemberToConversation(conversationId, memberId)
    )
  def getConversationMembers(conversationId: String): RIO[ChatMessageRepository, Set[String]] =
    ZIO.environmentWithZIO[ChatMessageRepository](_.get.getConversationMembers(conversationId))
  def getMembersStream(conversationId: String): ZStream[ChatMessageRepository, Nothing, String] =
    ZStream.environmentWithStream[ChatMessageRepository](_.get.getMembersStream(conversationId))
  def getAllMessages(conversationId: String): ZStream[ChatMessageRepository, Nothing, String] =
    ZStream.environmentWithStream[ChatMessageRepository](_.get.getAllMessages(conversationId))
  def storeMessage(
      conversationId: String,
      msg: String,
      timestamp: Double
  ): RIO[ChatMessageRepository, String] =
    ZIO.environmentWithZIO[ChatMessageRepository](
      _.get.storeMessage(conversationId, msg, timestamp)
    )
}
