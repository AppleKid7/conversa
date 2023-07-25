package com.conversa.config

import zio.config.magnolia.deriveConfig

final case class ChatConfig(maxNumberOfMembers: Int)
object ChatConfig {
  val config = deriveConfig[ChatConfig].nested("ChatConfig")
}