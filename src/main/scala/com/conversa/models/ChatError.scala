package com.conversa.models

enum ChatError {
  case ChatFull(message: String, maxCapacity: Int)
  case AlreadyJoined(message: String)
  case InvalidUserId(message: String)
  case InvalidConversationId(message: String)
  case InvalidJson(message: String)
  case NetworkReadError(message: String)
  case ShardcakeConnectionError(message: String)
  case DatabaseConnectionError(message: String)
  def message: String
}
