package com.conversa.auth.models

import zio.json.*

case class JwtPayload(username: String)
object JwtPayload {
  implicit val decoder: JsonDecoder[JwtPayload] = DeriveJsonDecoder.gen[JwtPayload]
}
