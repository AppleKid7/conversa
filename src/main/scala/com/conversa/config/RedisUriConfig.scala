package com.conversa.config

import zio.config.magnolia.deriveConfig

final case class RedisUriConfig(
    uri: String,
    host: String,
    username: String,
    password: String,
    port: Int
)

object RedisUriConfig {
  val config = deriveConfig[RedisUriConfig].nested("RedisUriConfig")
}
