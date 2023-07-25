package com.conversa.models

enum ChatError {
  case ChatFull(message: String, maxCapacity: Int)
  case AlreadyJoined(message: String)
  case InvalidUserId(message: String)
  case InvalidJson(message: String)
  case NetworkReadError(message: String)
  case ShardcakeConnectionError(message: String)
  def message: String
}
