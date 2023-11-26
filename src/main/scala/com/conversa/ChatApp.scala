package com.conversa

import com.conversa.auth.{Auth, JwtValidator}
import com.conversa.behaviors.ChatBehavior.*
import com.conversa.behaviors.ChatBehavior.ChatCommand.*
import com.conversa.config.OAuthConfig
//import com.conversa.config.ChatConfig
import com.conversa.config.ShardcakeConfig
//import com.conversa.db.RedisChatMessageRepository
//import com.conversa.models.ChatError
//import com.conversa.models.Message
//import com.conversa.session.Session
//import com.conversa.session.ShardcakeSession
import com.devsisters.shardcake.*
//import com.devsisters.shardcake.interfaces.Serialization
import dev.profunktor.redis4cats.RedisCommands
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.Uri
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.http.HttpAppMiddleware.bearerAuthZIO
import zio.{Config as _, *}

object ChatApp extends ZIOAppDefault {
  private def jwtValidate(token: String): URIO[Auth, Boolean] =
    for {
      validator <- ZIO.service[Auth]
      config <- ZIO
        .config[OAuthConfig](OAuthConfig.config)
        .orDieWith(e => new Throwable(e.getMessage))
      jwksUrl <- ZIO.fromEither(Uri.parse(config.jwksUrl)).orDieWith(e => new Throwable(e))
      result <- validator.validateJwtToken(token, jwksUrl).orDie
    } yield result

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider.fromResourcePath()
    )

  val config: ZLayer[Any, zio.Config.Error, com.devsisters.shardcake.Config] =
    ZLayer(ZIO.config[ShardcakeConfig](ShardcakeConfig.config).map { config =>
      com.devsisters.shardcake.Config.default.copy(shardingPort = config.port)
    })

  val register =
    for {
      _ <- Sharding.registerEntity(Conversation, behavior, p => Some(Terminate(p)))
      _ <- Sharding.registerScoped
    } yield ()

//  val program: ZIO[Session, Throwable, Unit] =
//    for {
//      session <- ZIO.service[Session]
//      user1 <- Random.nextUUID.map(_.toString)
//      user2 <- Random.nextUUID.map(_.toString)
//      user3 <- Random.nextUUID.map(_.toString)
//      user4 <- Random.nextUUID.map(_.toString)
//      conversationId <- session.createConversation.mapError(e => new Throwable(e.message))
//      _ <- session.joinConversation(conversationId, user1).mapError(e => new Throwable(e.message))
//      _ <- session.joinConversation(conversationId, user2).mapError(e => new Throwable(e.message))
//      _ <- session.joinConversation(conversationId, user3).mapError(e => new Throwable(e.message))
//      _ <- session
//        .sendMessageStream(conversationId, user1, "Hey, what's up")
//        .mapError(e => new Throwable(e.message))
//        .debug
//      _ <- session
//        .sendMessageStream(conversationId, user2, "Not much.")
//        .mapError(e => new Throwable(e.message))
//        .debug
//      _ <- session
//        .sendMessageStream(conversationId, user3, "Yeah, same.")
//        .mapError(e => new Throwable(e.message))
//        .debug
//      _ <- session
//        .sendMessageStream(conversationId, user4, "Hi, I'm error!")
//        .tapError(e => Console.printError(e.message))
//        .mapError(_ => ())
//        .fold(e => (), value => value)
//        .debug
//      _ <- session
//        .getMessages(conversationId)
//        .foreach(msg => Console.printLine(s"${msg.sender}: ${msg.content}"))
//      _ <- ZIO.never
//    } yield ()

//  def user: HttpApp[Any] = Routes(
//    Method.GET / "hello" / string("name") / "greet" -> handler { (name: String, _: Request) =>
//      Response.text(s"Welcome to the ZIO party! ${name}")
//    }
//  ).toHttpApp @@ bearerAuthZIO(jwtValidate(_))

  def user: HttpApp[Auth, Nothing] = Http.collect[Request] {
    case Method.GET -> Root / "user" / name / "greet" =>
      Response.text(s"Welcome to the ZIO party! ${name}")
  } @@ bearerAuthZIO(jwtValidate(_))

  val app: HttpApp[Auth, Nothing] = user

  // def run: Task[Unit] = ZIO.config[ChatConfig](ChatConfig.config).flatMap { chatConfig =>
  //   ZIO
  //     .scoped(register *> program)
  //     .provide(
  //       config,
  //       ZLayer.succeed(GrpcConfig.default),
  //       ZLayer.succeed(RedisConfig.default),
  //       redis,
  //       StorageRedis.live,
  //       KryoSerialization.live,
  //       ShardManagerClient.liveWithSttp,
  //       GrpcPods.live,
  //       Sharding.live,
  //       GrpcShardingService.live,
  //       ShardcakeSession.make(List.empty[Message], chatConfig.maxNumberOfMembers),
  //       RedisChatMessageRepository.live
  //     )
  // }

  override val run = zio
    .http
    .Server
    .serve(app)
    .provide(
      zio.http.Server.default,
      ZLayer.make[JwtValidator](JwtValidator.live, HttpClientZioBackend.layer())
    )
}
