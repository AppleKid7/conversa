package com.conversa.auth.helpers

import com.conversa.auth.models.JWKSet
import sttp.client3.*
import sttp.client3.ziojson.asJson
import sttp.model.Uri
import zio.*

final case class JWKSetFetcher(sttpBackend: SttpBackend[Task, Any]) {
  def fetchJwks(uri: Uri): Task[JWKSet] = {
    val request = basicRequest.response(asStringAlways).get(uri).response(asJson[JWKSet])
    sttpBackend.send(request).flatMap(result => ZIO.fromEither(result.body))
  }
}
object JWKSetFetcher {
  val live: ZLayer[SttpBackend[Task, Any], Any, JWKSetFetcher] =
    ZLayer.fromFunction(new JWKSetFetcher(_))
}
