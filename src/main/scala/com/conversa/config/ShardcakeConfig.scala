package com.conversa.config

import zio.config.magnolia.deriveConfig

final case class ShardcakeConfig(port: Int)
object ShardcakeConfig {
  val config = deriveConfig[ShardcakeConfig].nested("ShardcakeConfig")
}
