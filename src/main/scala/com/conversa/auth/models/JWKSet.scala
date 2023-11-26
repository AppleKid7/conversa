package com.conversa.auth.models

import zio.json.*

case class JWKSet(keys: List[JWK])
object JWKSet {
  implicit val decoder: JsonDecoder[JWKSet] = DeriveJsonDecoder.gen[JWKSet]
}
