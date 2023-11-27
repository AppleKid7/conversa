package com.conversa.auth

import zio.*

trait UserServices {
  def validateUsername(username: String): Task[Boolean]
}
