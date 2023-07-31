package com.conversa.session

import com.conversa.models.ChatError
import com.conversa.models.ConversationId
import com.conversa.models.Message
import com.conversa.models.UserId
import zio.*
import zio.stream.ZStream

trait Session {
  def createConversation: IO[ChatError, ConversationId]
  def joinConversation(connectionId: ConversationId, memberId: UserId): IO[ChatError, Unit]
  def sendMessage(
      connectionId: ConversationId,
      sender: String,
      content: String
  ): IO[ChatError, Message]
  def conversationEvents(connectionId: ConversationId): ZStream[Any, Nothing, Message]
  def getMessages(connectionId: ConversationId): ZStream[Any, Throwable, Message]
}

object Session {
  def createConversation: ZIO[Session, ChatError, ConversationId] =
    ZIO.environmentWithZIO[Session](_.get.createConversation)
  def joinConversation(
      connectionId: ConversationId,
      memberId: String,
      maxNumberOfMembers: Int
  ): ZIO[Session, ChatError, Unit] =
    ZIO.environmentWithZIO[Session](_.get.joinConversation(connectionId, memberId))
  def sendMessage(
      connectionId: ConversationId,
      sender: String,
      content: String
  ): ZIO[Session, ChatError, Message] =
    ZIO.environmentWithZIO[Session](_.get.sendMessage(connectionId, sender, content))
  def conversationEvents(connectionId: ConversationId): ZStream[Session, Throwable, Message] =
    ZStream.environmentWithStream[Session](_.get.conversationEvents(connectionId))
  def getMessages(connectionId: ConversationId) =
    ZStream.environmentWithStream[Session](_.get.getMessages(connectionId))
}
