package com.conversa.auth

import zio.*
import zio.aws.cognitoidentityprovider.CognitoIdentityProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials


final case class CognitoUserServices(client: CognitoIdentityProvider) extends UserServices {
  def validateUsername(username: String): Task[Boolean] = ???
}
object CognitoUserServices {
  val live: ZLayer[AwsBasicCredentials, Throwable, CognitoUserServices] = ???
}
