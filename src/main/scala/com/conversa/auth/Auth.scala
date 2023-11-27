package com.conversa.auth

import sttp.model.Uri
import zio.*

trait Auth {
  def validateJwtToken(token: String): Task[Boolean]
  def validateUsernameFromToken(token: String, username: String): Task[Boolean]
}
