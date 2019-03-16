package io.mkotsur.test

import cats.effect._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn

object App extends IOApp {

  import Decoders.implicits._
  import GithubApi._
  import Output.implicits._
  import cats.implicits._

  private def readGithubToken(envKey: String) = Option(System.getenv(envKey)) match {
    case None    => IO.raiseError(new RuntimeException(s"Please set $envKey env variable"))
    case Some(t) => IO.pure(Credentials.Token(CaseInsensitiveString("token"), t))
  }
  private def makeRepoInfosUri(username: String) = IO(Uri.uri("https://api.github.com/users/") / username / "repos")

  case class Contributor(login: String, contributions: Int)

  case class RepoInfo(full_name: String, contributors_url: String, fork: Boolean)

  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource.use { implicit client =>
      for {
        token        <- readGithubToken("GH_TOKEN")
        _            <- IO(println("Please enter your Github ID ?"))
        username     <- IO(StdIn.readLine())
        repoInfosUri <- makeRepoInfosUri(username)
        repoInfos    <- getAllPages[List[RepoInfo]](repoInfosUri, token)
        _            <- IO(println(s"Found ${repoInfos.length} repos"))
        contributorsPerRepo <- repoInfos
                                .filterNot(_.fork)
                                .traverse {
                                  case RepoInfo(repoName, contributionsUrl, _) =>
                                    for {
                                      uri          <- IO.fromEither(Uri.fromString(contributionsUrl))
                                      contributors <- getAllPages[List[Contributor]](uri, token)
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
