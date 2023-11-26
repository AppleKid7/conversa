package com.conversa.auth.models

import zio.json.*

case class JwtHeader(alg: String, kid: Option[String])
object JwtHeader {
  implicit val decoder: JsonDecoder[JwtHeader] = DeriveJsonDecoder.gen[JwtHeader]
}
