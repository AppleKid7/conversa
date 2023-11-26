package com.conversa.auth

import com.conversa.auth.helpers.JWKSetFetcher
import com.conversa.auth.models.*
import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import pdi.jwt.{Jwt, JwtAlgorithm, JwtOptions}
import sttp.client3.*
import sttp.client3.ziojson.asJson
import sttp.model.Uri
import zio.*
import zio.json.*
import com.conversa.config.OAuthConfig

final case class JwtValidator(jwkSet: JWKSet) extends Auth {
  def decodeBase64Url(str: String): BigInteger = {
    new BigInteger(1, Base64.getUrlDecoder.decode(str))
  }

  def getPublicKey(jwks: JWKSet, keyId: String): Option[PublicKey] = {
    jwks.keys.find(_.kid == keyId).map { jwk =>
      val modulus = decodeBase64Url(jwk.n)
      val exponent = decodeBase64Url(jwk.e)
      val spec = new RSAPublicKeySpec(modulus, exponent)
      KeyFactory.getInstance("RSA").generatePublic(spec)
    }
  }

  def validateToken(token: String, publicKey: PublicKey): Boolean = {
    Jwt.isValid(token, publicKey, Seq(JwtAlgorithm.RS256))
  }

  override def validateJwtToken(token: String): Task[Boolean] = {
    for {
      decodedToken <- ZIO.fromTry(Jwt.decodeRawAll(token, JwtOptions(signature = false)))
      headerJson = decodedToken._1
      jwtHeader <- ZIO.fromEither(headerJson.fromJson[JwtHeader].left.map(new RuntimeException(_)))
      publicKeyOpt = jwtHeader.kid.flatMap(kid => getPublicKey(jwkSet, kid))
      result = publicKeyOpt.exists(validateToken(token, _))
    } yield result
  }

  override def validateUsername(token: String, username: String): Task[Boolean] = {
    for {
      decodedToken <- ZIO.fromTry(Jwt.decodeRawAll(token, JwtOptions(signature = false)))
      headerJson = decodedToken._1
      jwtHeader <- ZIO.fromEither(headerJson.fromJson[JwtHeader].left.map(new RuntimeException(_)))
      publicKeyOpt = jwtHeader.kid.flatMap(kid => getPublicKey(jwkSet, kid))
      isValid = publicKeyOpt.exists(publicKey =>
        Jwt.isValid(token, publicKey, Seq(JwtAlgorithm.RS256))
      )
      payloadJson = decodedToken._2
      cognitoUsername <- ZIO
        .fromEither(payloadJson.fromJson[JwtPayload].left.map(new RuntimeException(_)))
        .map(_.username)
    } yield isValid && cognitoUsername == username
  }
}

object JwtValidator {
  val live = ZLayer.scoped {
    for {
      jwkSetFetcher <- ZIO.service[JWKSetFetcher]
      config <- ZIO
        .config[OAuthConfig](OAuthConfig.config)
        .orDieWith(e => new Throwable(e.getMessage))
      jwksUrl <- ZIO.fromEither(Uri.parse(config.jwksUrl)).orDieWith(e => new Throwable(e))
      jwks <- jwkSetFetcher.fetchJwks(jwksUrl)
    } yield JwtValidator(jwks)
  }
}
