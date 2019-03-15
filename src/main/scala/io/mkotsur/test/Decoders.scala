package io.mkotsur.test
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import cats.{ Applicative, Monad, Traverse }
import io.circe.generic.auto._
import io.mkotsur.test.App.{ Contributor, RepoInfo }
import org.http4s.circe.jsonOf
import org.http4s.headers.Link
import org.http4s.{ DecodeFailure, DecodeResult, EntityDecoder, HttpVersion, MediaRange, Message, Response, _ }
object Decoders {

  object implicits {
    implicit val repoInfosDecoder             = jsonOf[IO, List[RepoInfo]]
    implicit val paginatedRepoInfosDecoder    = Decoders.paginated[IO, List[RepoInfo]]
    implicit val contributorsDecoder          = jsonOf[IO, List[Contributor]]
    implicit val paginatedContributorsDecoder = Decoders.paginated[IO, List[Contributor]]
  }

  object paginated {
    def apply[F[_]: Monad, E](implicit bodyDecoder: EntityDecoder[F, E]): EntityDecoder[F, (E, Option[Uri])] =
      new EntityDecoder[F, (E, Option[Uri])] {

        override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, (E, Option[Uri])] = {
          val linkHeaderParseResults =
            msg.headers.filter(_.name === Link.name).flatMap(_.value.split(",")).map(h => Link.parse(h.trim)).toList
          val linkHeadersEither = Traverse[List]
            .traverse(linkHeaderParseResults)(identity)
            .left
            .map(
              pf =>
                new DecodeFailure {
                  override def message: String          = pf.getMessage()
                  override def cause: Option[Throwable] = Some(pf)
                  override def toHttpResponse[F[_]](
                    httpVersion: HttpVersion
                  )(implicit F: Applicative[F]): F[Response[F]] = ???
                }
            )

          for {
            body  <- bodyDecoder.decode(msg, strict = true)
            links <- EitherT.fromEither[F](linkHeadersEither)
          } yield {
            val uriOption = links.find(_.rel.contains("next")).map(_.uri)
            (body, uriOption)
          }
        }

        override def consumes: Set[MediaRange] = bodyDecoder.consumes
      }
  }

}
