package com.conversa.auth

import com.conversa.config.OauthConfig
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio.*
import zio.http.*
import zio.http.Middleware.bearerAuth

final case class CognitoAuth(secretKey: String) extends Auth {
  private def jwtDecode(token: String): Option[JwtClaim] =
    Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS512)).toOption

  override def validateToken(token: String): HandlerAspect[Any, Unit] =
    bearerAuth(jwtDecode(_).isDefined)
}

object CognitoAuth {
  // val live: ZLayer[Any, Config.Error, CognitoAuth] = ZLayer.scoped {
  //   for {
  //     config <- ZIO.config[OauthConfig](OauthConfig.config)
  //   } yield CognitoAuth(config.secretKey)
  // }
  val live: ZIO[Any, Config.Error, CognitoAuth] =
    ZIO.config[OauthConfig](OauthConfig.config).map(config => CognitoAuth(config.secretKey))
}
