package com.conversa.auth

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

final case class JwtValidator(sttpBackend: SttpBackend[Task, Any]) extends Auth {
  def decodeBase64Url(str: String): BigInteger = {
    new BigInteger(1, Base64.getUrlDecoder.decode(str))
  }

  def fetchJwks(uri: Uri): Task[JWKSet] = {
    val request = basicRequest.response(asStringAlways).get(uri).response(asJson[JWKSet])
    sttpBackend.send(request).flatMap(result => ZIO.fromEither(result.body))
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

  override def validateJwtToken(token: String, jwksUrl: Uri): Task[Boolean] = {
    for {
      jwks <- fetchJwks(jwksUrl)
      decodedToken <- ZIO.fromTry(Jwt.decodeRawAll(token, JwtOptions(signature = false)))
      headerJson = decodedToken._1
      jwtHeader <- ZIO.fromEither(headerJson.fromJson[JwtHeader].left.map(new RuntimeException(_)))
      publicKeyOpt = jwtHeader.kid.flatMap(kid => getPublicKey(jwks, kid))
      result = publicKeyOpt.exists(validateToken(token, _))
    } yield result
  }

  case class JWK(kid: String, kty: String, alg: String, use: String, n: String, e: String)

  object JWK {
    implicit val decoder: JsonDecoder[JWK] = DeriveJsonDecoder.gen[JWK]
  }

  case class JWKSet(keys: List[JWK])

  object JWKSet {
    implicit val decoder: JsonDecoder[JWKSet] = DeriveJsonDecoder.gen[JWKSet]
  }

  case class JwtHeader(alg: String, kid: Option[String])

  object JwtHeader {
    implicit val decoder: JsonDecoder[JwtHeader] = DeriveJsonDecoder.gen[JwtHeader]
  }
}

object JwtValidator {
  val live: ZLayer[SttpBackend[Task, Any], Any, JwtValidator] =
    ZLayer.fromFunction(new JwtValidator(_))
}
