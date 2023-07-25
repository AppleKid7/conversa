package com.conversa.db

import com.conversa.models.Message

trait ChatMessageRepository {
  def getAllMessages: List[Message]
  def storeMessage: Message
}
