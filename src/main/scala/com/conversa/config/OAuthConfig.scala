package com.conversa.config

import zio.config.magnolia.deriveConfig

final case class OAuthConfig(jwksUrl: String)
object OAuthConfig {
  val config = deriveConfig[OAuthConfig].nested("OAuthConfig")
}
