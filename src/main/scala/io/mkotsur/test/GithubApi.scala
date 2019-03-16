package io.mkotsur.test

import cats.Semigroup
import cats.effect._
import cats.implicits._
import io.circe.generic.auto._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.{ EntityDecoder, _ }

object GithubApi {

  /**
   * Fetches and combines results from all pages. The method expects a special entity decoder,
   * which returns not only a decoded entity, but also the URI of the next page if it exists.
   */
  def getAllPages[A: Semigroup](
    uri: Uri,
    token: Credentials.Token
  )(implicit decoder: EntityDecoder[IO, (A, Option[Uri])], client: Client[IO]): IO[A] = {
    val req = Method.GET(uri, Authorization(token))
    client.expect[(A, Option[Uri])](req).flatMap {
      case (items, None) => IO.pure(items)
      case (items, Some(nextPageUri)) =>
        getAllPages[A](nextPageUri, token).map { newItems =>
          newItems |+| items
        }
    }
  }

}
