package com.conversa.db

import zio.*
import zio.aws.core.AwsError
import zio.aws.core.aspects.*
import zio.aws.core.config.{AwsConfig, CommonAwsConfig}
import zio.aws.dynamodb.*
import zio.aws.dynamodb.model.*
import zio.aws.dynamodb.model.primitives.*
import zio.aws.dynamodb.{DynamoDb, model}
import zio.aws.netty.NettyHttpClient
import zio.stream.*

final case class DyDbChatMessageRepository(dydbClient: DynamoDb) extends ChatMessageRepository {
  override def createConversation(conversationId: String): Task[Unit] =
    for {
      _ <- dydbClient
        .putItem(
          PutItemRequest(
            tableName = TableName("conversations"),
            item = Map(
              AttributeName("id") -> AttributeValue(s = Some(StringAttributeValue(conversationId)))
            )
          )
        )
        .mapError(e => e.toThrowable)
    } yield ()
  override def entityExists(entityId: String, entityType: String): Task[Boolean] =
    for {
      res <- dydbClient
        .getItem(
          GetItemRequest(
            tableName = TableName(entityType),
            key = Map(
              AttributeName("id") -> AttributeValue(s = Some(StringAttributeValue(entityId)))
            )
          )
        )
        .mapError(e => e.toThrowable)
      item <- res.getItem.mapError(e => e.toThrowable)
    } yield item.nonEmpty

  // def getMembersForTopic(topicId: String): ZIO[Blocking with DynamoDbClient, Throwable, List[String]] = {
  //   val queryRequest = QueryRequest.builder()
  //     .tableName("YourTableName") // Replace 'YourTableName' with your actual DynamoDB table name.
  //     .keyConditionExpression("topicId = :topicId")
  //     .expressionAttributeValues(Map(":topicId" -> AttributeValue.builder().s(topicId).build()))
  //     .build()

  //   for {
  //     client <- ZIO.access[DynamoDbClient](_.get)
  //     queryResponse <- ZIO.fromCompletionStage(client.query(queryRequest))
  //   } yield {
  //     val items = queryResponse.items().asScala.toList
  //     items.flatMap(item => Option(item.get("memberId")).map(_.s()))
  //   }
  // }

  override def getConversationMembers(conversationId: String): Task[Set[String]] =
    for {
      res <- dydbClient
        .query(
          QueryRequest(
            tableName = TableName("conversations"),
            keyConditionExpression = KeyExpression("conversationId = :c_id"),
            expressionAttributeValues = Map(
              ExpressionAttributeValueVariable(":c_id") -> AttributeValue(s =
                Some(StringAttributeValue(conversationId))
              )
            )
          )
        )
        .mapError(e => e.toThrowable)
        .runCollect
      result = res.map { map => map.get(AttributeName("userId")).get.s.getOrElse("") }
    } yield result.toSet

  override def getMembersStream(conversationId: String): ZStream[Any, Nothing, String] =
    for {
      res <- dydbClient
        .query(
          QueryRequest(
            tableName = TableName("conversations"),
            keyConditionExpression = KeyExpression("conversationId = :c_id"),
            expressionAttributeValues = Map(
              ExpressionAttributeValueVariable(":c_id") -> AttributeValue(s =
                Some(StringAttributeValue(conversationId))
              )
            )
          )
        )
        .orDieWith(_.toThrowable)
      result = res.get(AttributeName("userId")).get.s.getOrElse("")
    } yield result

  override def addMemberToConversation(conversationId: String, memberId: String): Task[Unit] =
    for {
      _ <- dydbClient
        .putItem(
          PutItemRequest(
            tableName = TableName("conversations"),
            item = Map(
              AttributeName("conversationId") -> AttributeValue(s =
                Some(StringAttributeValue(conversationId))
              ),
              AttributeName("userId") -> AttributeValue(s = Some(StringAttributeValue(memberId)))
            )
          )
        )
        .mapError(e => e.toThrowable)
    } yield ()
  override def getAllMessages(conversationId: String): ZStream[Any, Nothing, String] =
    for {
      res <- dydbClient
        .query(
          QueryRequest(
            tableName = TableName("messages"),
            keyConditionExpression = KeyExpression("conversationId = :c_id"),
            expressionAttributeValues = Map(
              ExpressionAttributeValueVariable(":c_id") -> AttributeValue(s =
                Some(StringAttributeValue(conversationId))
              )
            )
          )
        )
        .orDieWith(_.toThrowable)
      result = res.get(AttributeName("content")).get.s.getOrElse("")
    } yield result

  override def storeMessage(conversationId: String, msg: String, timestamp: Double): Task[String] =
    for {
      _ <- dydbClient
        .putItem(
          PutItemRequest(
            tableName = TableName("messages"),
            item = Map(
              AttributeName("conversationId") -> AttributeValue(s =
                Some(StringAttributeValue(conversationId))
              ),
              AttributeName("content") -> AttributeValue(s = Some(StringAttributeValue(msg))),
              AttributeName("timestamp") -> AttributeValue(n =
                Some(NumberAttributeValue(timestamp.toString()))
              )
            )
          )
        )
        .mapError(e => e.toThrowable)
    } yield msg

  override def getUserPassword(userId: String): Task[Option[String]] =
    for {
      res <- dydbClient
        .getItem(
          GetItemRequest(
            tableName = TableName("users"),
            key = Map(
              AttributeName("id") -> AttributeValue(s = Some(StringAttributeValue(userId)))
            ),
            attributesToGet = Seq(
              AttributeName("password")
            )
          )
        )
        .mapError(e => e.toThrowable)
      item <- res
        .getItem
        .orDieWith(_.toThrowable)
        .map(_.get(AttributeName("password")).get.s.toOption)
    } yield item

  override def createUser(userId: String, encryptedPassword: String): Task[Unit] =
    for {
      _ <- dydbClient
        .putItem(
          PutItemRequest(
            tableName = TableName("users"),
            item = Map(
              AttributeName("userId") -> AttributeValue(s = Some(StringAttributeValue(userId))),
              AttributeName("password") -> AttributeValue(s =
                Some(StringAttributeValue(encryptedPassword))
              )
            )
          )
        )
        .mapError(e => e.toThrowable)
    } yield ()
}
object DyDbChatMessageRepository {
  val live = ZLayer.scoped {
    for {
      dydb <- ZIO.service[DynamoDb]
    } yield DyDbChatMessageRepository(dydb)
  }
}
