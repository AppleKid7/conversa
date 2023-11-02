package integtests.com.conversa

import com.conversa.behaviors.ChatBehavior.*
import com.conversa.behaviors.ChatBehavior.ChatCommand.*
import com.conversa.db.DyDbChatMessageRepository
import com.conversa.models.Message
import com.conversa.session.Session
import com.conversa.session.ShardcakeSession
import com.devsisters.shardcake.*
import com.devsisters.shardcake.StorageRedis.Redis
import com.devsisters.shardcake.interfaces.PodsHealth
import com.devsisters.shardcake.interfaces.Serialization
import dev.profunktor.redis4cats.RedisCommands
import sttp.client3.UriContext
import zio.aws.core.AwsError
import zio.aws.dynamodb.DynamoDb
import zio.aws.dynamodb.model.*
import zio.aws.dynamodb.model.TableDescription.ReadOnly
import zio.aws.dynamodb.model.primitives.*
import zio.stream.ZSink
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.{Config => _, _}

object ChatApiSpec extends ZIOSpecDefault {

  private val config = ZLayer.succeed(
    Config
      .default
      .copy(
        shardManagerUri = uri"http://localhost:8087/api/graphql",
        simulateRemotePods = true,
        sendTimeout = 10.seconds
      )
  )
  private val grpcConfig = ZLayer.succeed(GrpcConfig.default)
  private val managerConfig = ZLayer.succeed(ManagerConfig.default.copy(apiPort = 8087))
  private val redisConfig = ZLayer.succeed(RedisConfig.default)

  private val testConversationsTable: ZIO[DynamoDb & Scope, AwsError, ReadOnly] = {
    for {
      dynamodb <- ZIO.service[DynamoDb]
      conversationsTableName = TableName("conversations")
      managedTableResource <- ZIO.acquireRelease(
        for {
          _ <- Console.printLine(s"Creating table $conversationsTableName").ignore
          tableData <- DynamoDb.createTable(
            CreateTableRequest(
              tableName = conversationsTableName,
              attributeDefinitions = List(
                AttributeDefinition(
                  KeySchemaAttributeName("id"),
                  ScalarAttributeType.S
                ),
                AttributeDefinition(
                  KeySchemaAttributeName("userId"),
                  ScalarAttributeType.S
                )
              ),
              keySchema = List(
                KeySchemaElement(KeySchemaAttributeName("id"), KeyType.HASH),
                KeySchemaElement(KeySchemaAttributeName("userId"), KeyType.RANGE)
              ),
              provisionedThroughput = Some(
                ProvisionedThroughput(
                  readCapacityUnits = PositiveLongObject(16L),
                  writeCapacityUnits = PositiveLongObject(16L)
                )
              )
            )
          )
          tableDesc <- tableData.getTableDescription
        } yield tableDesc
      )(tableDescription =>
        tableDescription
          .getTableName
          .flatMap { tableName =>
            for {
              _ <- Console.printLine(s"Deleting table $tableName").ignore
              _ <- DynamoDb.deleteTable(DeleteTableRequest(tableName))
            } yield ()
          }
          .provideEnvironment(ZEnvironment(dynamodb))
          .catchAll(error => ZIO.die(error.toThrowable))
          .unit
      )
    } yield managedTableResource
  }

  private val testMessagesTable: ZIO[DynamoDb & Scope, AwsError, ReadOnly] = {
    for {
      dynamodb <- ZIO.service[DynamoDb]
      conversationsTableName = TableName("messages")
      managedTableResource <- ZIO.acquireRelease(
        for {
          _ <- Console.printLine(s"Creating table $conversationsTableName").ignore
          tableData <- DynamoDb.createTable(
            CreateTableRequest(
              tableName = conversationsTableName,
              attributeDefinitions = List(
                AttributeDefinition(
                  KeySchemaAttributeName("id"),
                  ScalarAttributeType.S
                ),
                AttributeDefinition(
                  KeySchemaAttributeName("messageId"),
                  ScalarAttributeType.S
                ),
              ),
              keySchema = List(
                KeySchemaElement(KeySchemaAttributeName("id"), KeyType.HASH),
                KeySchemaElement(KeySchemaAttributeName("messageId"), KeyType.RANGE)
              ),
              provisionedThroughput = Some(
                ProvisionedThroughput(
                  readCapacityUnits = PositiveLongObject(16L),
                  writeCapacityUnits = PositiveLongObject(16L)
                )
              )
            )
          )
          tableDesc <- tableData.getTableDescription
        } yield tableDesc
      )(tableDescription =>
        tableDescription
          .getTableName
          .flatMap { tableName =>
            for {
              _ <- Console.printLine(s"Deleting table $tableName").ignore
              _ <- DynamoDb.deleteTable(DeleteTableRequest(tableName))
            } yield ()
          }
          .provideEnvironment(ZEnvironment(dynamodb))
          .catchAll(error => ZIO.die(error.toThrowable))
          .unit
      )
    } yield managedTableResource
  }

  def spec: Spec[TestEnvironment with zio.Scope, Any] =
    suite("ChatBehavior end to end test")(
      test("Creates conversation and adds a user to it") {
        ZIO.scoped {
          for {
            _ <- Sharding.registerEntity(Conversation, behavior, p => Some(Terminate(p)))
            _ <- Sharding.registerScoped
            _ <- testMessagesTable
            _ <- testConversationsTable
            session <- ZIO.service[Session]
            user1 <- Random.nextUUID.map(_.toString)
            user2 <- Random.nextUUID.map(_.toString)
            user3 <- Random.nextUUID.map(_.toString)
            conversationId <- session
              .createConversation
              .mapError(e => new Throwable(e.message))
              .debug
            _ <- session
              .joinConversation(conversationId, user1)
              .mapError(e => new Throwable(e.message))
              .debug
             _ <- session
               .joinConversation(conversationId, user2)
               .mapError(e => new Throwable(e.message))
               .debug
            // _ <- session
            //   .joinConversation(conversationId, user3)
            //   .mapError(e => new Throwable(e.message))
            //   .debug
            _ <- session
              .sendMessage(conversationId, user1, "Hey, what's up")
              .mapError(e => new Throwable(e.message))
              .tapError(e => Console.printLine(s"TEST: ${e.getMessage()}"))
              .debug
             _ <- session
               .sendMessage(conversationId, user2, "Not much.")
               .mapError(e => new Throwable(e.message))
               .debug
            // _ <- session
            //   .sendMessage(conversationId, user3, "Yeah, same.")
            //   .mapError(e => new Throwable(e.message))
            //   .debug
            // failure <- session
            //   .sendMessageStream(conversationId, user4, "Hi, I'm error!")
            //   .tapError(e => Console.printError(e.message))
            //   .fold(e => (), value => value)
            //   .debug
             messages <- session.getMessages(conversationId).run(ZSink.collectAll[Message])
          } yield assert(messages)(hasSize(equalTo(2))) /* yield assertTrue(
            3 == 3
          )*/
        }
      }
    ).provideShared(
      config,
      ZLayer.succeed(GrpcConfig.default),
      ZLayer.succeed(RedisConfig.default),
      redis,
      container,
      ShardManager.live,
      shardManagerServer,
      managerConfig,
      StorageRedis.live,
      KryoSerialization.live,
      ShardManagerClient.liveWithSttp,
      GrpcPods.live,
      PodsHealth.noop,
      Sharding.live,
      GrpcShardingService.live,
      ShardcakeSession.make(List.empty[Message], 5),
      DyDbChatMessageRepository.live,
      TestLocalstack.dynamoDb,
      TestLocalstack.awsConfig
    ) @@ sequential @@ withLiveClock @@ nondeterministic @@ flaky

  // val program: ZIO[Session, Throwable, Unit] =
  //   for {
  //     session <- ZIO.service[Session]
  //     user1 <- Random.nextUUID.map(_.toString)
  //     user2 <- Random.nextUUID.map(_.toString)
  //     user3 <- Random.nextUUID.map(_.toString)
  //     user4 <- Random.nextUUID.map(_.toString)
  //     conversationId <- session.createConversation.mapError(e => new Throwable(e.message))
  //     _ <- session.joinConversation(conversationId, user1).mapError(e => new Throwable(e.message))
  //     _ <- session.joinConversation(conversationId, user2).mapError(e => new Throwable(e.message))
  //     _ <- session.joinConversation(conversationId, user3).mapError(e => new Throwable(e.message))
  //     _ <- session
  //       .sendMessageStream(conversationId, user1, "Hey, what's up")
  //       .mapError(e => new Throwable(e.message))
  //       .debug
  //     _ <- session
  //       .sendMessageStream(conversationId, user2, "Not much.")
  //       .mapError(e => new Throwable(e.message))
  //       .debug
  //     _ <- session
  //       .sendMessageStream(conversationId, user3, "Yeah, same.")
  //       .mapError(e => new Throwable(e.message))
  //       .debug
  //     _ <- session
  //       .sendMessageStream(conversationId, user4, "Hi, I'm error!")
  //       .tapError(e => Console.printError(e.message))
  //       .fold(e => (), value => value)
  //       .debug
  //     _ <- session
  //       .getMessages(conversationId)
  //       .foreach(msg => Console.printLine(s"${msg.sender}: ${msg.content}"))
  //     _ <- ZIO.never
  //   } yield ()

  // def run: Task[Unit] = ZIO.config[ChatConfig](ChatConfig.config).flatMap { chatConfig =>
  //   ZIO
  //     .scoped(register *> program)
  //     .provide(
  //       config,
  //       ZLayer.succeed(GrpcConfig.default),
  //       ZLayer.succeed(RedisConfig.default),
  //       redis,
  //       container,
  //       StorageRedis.live,
  //       KryoSerialization.live,
  //       ShardManagerClient.liveWithSttp,
  //       GrpcPods.live,
  //       Sharding.live,
  //       GrpcShardingService.live,
  //       ShardcakeSession.make(List.empty[Message], chatConfig.maxNumberOfMembers),
  //       DyDbChatMessageRepository.live,
  //       TestLocalstack.dynamoDb,
  //       TestLocalstack.awsConfig
  //     )
  // }
}
