package com.conversa.auth

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtOptions}
import scala.util.{Try, Failure, Success}
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}
import java.math.BigInteger
import java.util.Base64
import sttp.client3.*
import zio.*
import zio.{Runtime, Task}
import zio.json.*

import scala.concurrent.Future
import sttp.client3.*
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.ziojson.asJson
import sttp.model.Uri
// import com.conversa.config.OauthConfig
// import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
// import zio.*
// import zio.http.*
// import zio.http.Middleware.bearerAuth

// final case class CognitoAuth(secretKey: String) extends Auth {
//   private def jwtDecode(token: String): Option[JwtClaim] =
//     Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS512)).toOption

//   override def validateToken(token: String): HandlerAspect[Any, Unit] =
//     bearerAuth(jwtDecode(_).isDefined)
// }

// object CognitoAuth {
//   // val live: ZLayer[Any, Config.Error, CognitoAuth] = ZLayer.scoped {
//   //   for {
//   //     config <- ZIO.config[OauthConfig](OauthConfig.config)
//   //   } yield CognitoAuth(config.secretKey)
//   // }
//   val live: ZIO[Any, Config.Error, CognitoAuth] =
//     ZIO.config[OauthConfig](OauthConfig.config).map(config => CognitoAuth(config.secretKey))
// }
val token = "example.jwt.token"
val jwksUrl = uri"https://example-jwks-url"

final case class JwtValidator(sttpBackend: SttpBackend[Task, Any]) {
  // Helper function to convert Base64 URL encoded string to BigInteger
  def decodeBase64Url(str: String): BigInteger = {
    new BigInteger(1, Base64.getUrlDecoder.decode(str))
  }

  // Fetch JWKS and parse it
  def fetchJwks(uri: Uri): Task[JWKSet] = {
      val request = basicRequest.response(asStringAlways).get(uri).response(asJson[JWKSet])
      sttpBackend.send(request).flatMap(result => ZIO.fromEither(result.body))
  }

  // Find the public key from JWKS
  def getPublicKey(jwks: JWKSet, keyId: String): Option[PublicKey] = {
    jwks.keys.find(_.kid == keyId).map { jwk =>
      val modulus = decodeBase64Url(jwk.n)
      val exponent = decodeBase64Url(jwk.e)
      val spec = new RSAPublicKeySpec(modulus, exponent)
      KeyFactory.getInstance("RSA").generatePublic(spec)
    }
  }

  // Validate the token
  def validateToken(token: String, publicKey: PublicKey): Boolean = {
    Jwt.isValid(token, publicKey, Seq(JwtAlgorithm.RS256))
  }

  // Main logic to validate the JWT
  def validateJwtToken(token: String, jwksUrl: Uri): Task[Boolean] = {
    for {
      jwks <- fetchJwks(jwksUrl)
      // decodedTokenZIO <- ZIO.attempt(Jwt.decodeRawAll(token, JwtOptions(signature = false)))
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

  case class JwtHeader(alg: String, typ: String, kid: Option[String])

  object JwtHeader {
    implicit val decoder: JsonDecoder[JwtHeader] = DeriveJsonDecoder.gen[JwtHeader]
  }
}
