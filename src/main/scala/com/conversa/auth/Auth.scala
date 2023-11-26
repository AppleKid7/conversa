package com.conversa.auth

import sttp.model.Uri
import zio.*

trait Auth {
  def validateJwtToken(token: String, jwksUrl: Uri): Task[Boolean]
}
