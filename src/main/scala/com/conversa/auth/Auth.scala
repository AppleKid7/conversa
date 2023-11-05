package com.conversa.auth

import zio.http.*

trait Auth {
  def validateToken(token: String): HandlerAspect[Any, Unit]
}
