package com.conversa.models

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder}

final case class Message(conversationId: String, timestamp: Double, sender: String, content: String)
object Message {
  val empty = Message(
    "",
    0,
    "",
    ""
  )
  given zio.json.JsonEncoder[Message] =
    DeriveJsonEncoder.gen[Message]

  given zio.json.JsonDecoder[Message] =
    DeriveJsonDecoder.gen[Message]
}
