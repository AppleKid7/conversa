package integtests.com.conversa

import zio.aws.core.aspects.*
import zio.aws.core.*
import zio.*

trait Logging {
  val callLogging: AwsCallAspect[Any] =
    new AwsCallAspect[Any] {
      override final def apply[R, E >: AwsError, A <: Described[_]](
          f: ZIO[R, E, A]
      )(implicit trace: Trace): ZIO[R, E, A] = {
        f.either.timed
          .flatMap {
            case (duration, Right(r)) =>
              ZIO.succeed(r)
            case (duration, Left(error)) =>
              Console
                .printLine(
                  s"AWS call FAILED in $duration with $error"
                )
                .ignore *> ZIO.fail(error)
          }
      }
    }
}
