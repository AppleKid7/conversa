package com.conversa.models

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder}

type UserId = String

final case class User(userId: UserId, username: String, password: String)
object User {
  given zio.json.JsonEncoder[User] = DeriveJsonEncoder.gen[User]
  given zio.json.JsonDecoder[User] = DeriveJsonDecoder.gen[User]
}
