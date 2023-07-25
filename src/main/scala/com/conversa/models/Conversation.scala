package com.conversa.models

import zio.json.*

type ConversationId = String

final case class Conversation(id: ConversationId, user: UserId, slug: List[String])

object Conversation {
  given zio.json.JsonEncoder[Conversation] = DeriveJsonEncoder.gen[Conversation]
  given zio.json.JsonDecoder[Conversation] = DeriveJsonDecoder.gen[Conversation]
}
