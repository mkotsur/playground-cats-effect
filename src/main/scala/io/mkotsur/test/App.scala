package io.mkotsur.test

import cats.effect._
import cats.implicits._
import io.circe.generic.auto._
import org.http4s._

import org.http4s.client.Client
import org.http4s.client.blaze._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn

object App extends IOApp {

  import Decoders.implicits._

  private def readGithubToken(envKey: String) = Option(System.getenv(envKey)) match {
    case None    => IO.raiseError(new RuntimeException(s"Please set $envKey env variable"))
    case Some(t) => IO.pure(Credentials.Token(CaseInsensitiveString("token"), t))
  }

  private def getContributors(uri: Uri, token: Credentials.Token)(implicit client: Client[IO]): IO[List[Contributor]] =
    client.expect[(List[Contributor], Option[Uri])](Method.GET(uri, Authorization(token))).flatMap {
      case (contributors, None) => IO.pure(contributors)
      case (contributors, Some(nextPageUri)) =>
        getContributors(nextPageUri, token).map { contribsNext =>
          contribsNext ::: contributors
        }
    }

  private def getRepoInfosUri(username: String) = IO(Uri.uri("https://api.github.com/users/") / username / "repos")

  private def getRepoInfos(uri: Uri, token: Credentials.Token)(implicit client: Client[IO]): IO[List[RepoInfo]] = {
    val req = Method.GET(uri, Authorization(token))
    client.expect[(List[RepoInfo], Option[Uri])](req).flatMap {
      case (repoInfos, None) => IO.pure(repoInfos)
      case (repoInfos, Some(nextPageUri)) =>
        getRepoInfos(nextPageUri, token).map { next =>
          next ::: repoInfos
        }
    }
  }

  case class Contributor(login: String, contributions: Int)

  case class RepoInfo(full_name: String, contributors_url: String, fork: Boolean)

  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource.use { implicit client =>
      import Output.implicits._
      for {
        token        <- readGithubToken("GH_TOKEN")
        _            <- IO(println("Please enter your Github ID ?"))
        username     <- IO(StdIn.readLine())
        repoInfosUri <- getRepoInfosUri(username)
        repoInfos    <- getRepoInfos(repoInfosUri, token)
        _            <- IO(println(s"Found ${repoInfos.length} repos"))
        contributorsPerRepo <- repoInfos
                                .filterNot(_.fork)
                                .traverse {
                                  case RepoInfo(repoName, contributionsUrl, _) =>
                                    for {
                                      uri          <- IO.fromEither(Uri.fromString(contributionsUrl))
                                      contributors <- getContributors(uri, token)
                                    } yield {
                                      val orderedContributors = contributors
                                        .filterNot(_.login === username)
                                        .sortBy(_.contributions)(Ordering[Int].reverse)
                                      (repoName, orderedContributors)
                                    }
                                }
        _ <- IO(println(contributorsPerRepo.show))
      } yield ExitCode.Success
    }
}
