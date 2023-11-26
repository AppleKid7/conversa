package com.conversa.auth.models

import zio.json.*

case class JWK(kid: String, kty: String, alg: String, use: String, n: String, e: String)
object JWK {
  implicit val decoder: JsonDecoder[JWK] = DeriveJsonDecoder.gen[JWK]
}
