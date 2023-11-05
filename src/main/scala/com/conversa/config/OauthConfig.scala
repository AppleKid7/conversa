package com.conversa.config

import zio.config.magnolia.deriveConfig

final case class OauthConfig(secretKey: String)
object OauthConfig {
  val config = deriveConfig[OauthConfig].nested("ChatConfig")
}
