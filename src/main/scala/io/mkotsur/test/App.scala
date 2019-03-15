package io.mkotsur.test

import cats.Show
import cats.effect._
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn

object App extends IOApp {

  private def repositoriesUrls(username: String, token: Credentials.Token) = Method.GET(
    Uri.uri("https://api.github.com/users/") / username / "repos",
    Authorization(token)
  )

  private def contributorsInfo(repoUrl: String, token: Credentials.Token): IO[Request[IO]] =
    for {
      uri <- IO.fromEither(Uri.fromString(repoUrl))
      req <- Method.GET(uri, Authorization(token))
    } yield req

  case class Contributor(login: String, contributions: Int)
  case class RepoInfo(full_name: String, contributors_url: String, fork: Boolean)

  implicit val contributionsShow = new Show[(String, List[Contributor])] {
    override def show(t: (String, List[Contributor])): String = t match {
      case (repoName, Nil)          => s"$repoName ->  ~~ no contributions ~~ \n\n"
      case (repoName, contributors) => s"$repoName -> \n" + contributors.mkString("\n") + "\n\n"
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {

    import cats.implicits._
    import io.circe.generic.auto._
    import org.http4s._
    import org.http4s.circe._

    implicit val seqContributerDecoder: EntityDecoder[IO, List[Contributor]] = jsonOf[IO, List[Contributor]]
    implicit val seqRepoInfosDecoder: EntityDecoder[IO, List[RepoInfo]]      = jsonOf[IO, List[RepoInfo]]

    BlazeClientBuilder[IO](global).resource.use { client =>
      for {
        token <- Option(System.getenv("GH_TOKEN")) match {
                  case None    => IO.raiseError(new RuntimeException("Please set GH_TOKEN env variable"))
                  case Some(t) => IO.pure(Credentials.Token(CaseInsensitiveString("token"), t))
                }
        _         <- IO(println("Please enter your Github ID ?"))
        name      <- IO(StdIn.readLine())
        repoInfos <- client.expect[List[RepoInfo]](repositoriesUrls(name, token))
        contributionsPerRepo <- {
          repoInfos
            .filterNot(_.fork)
            .traverse {
              case RepoInfo(repoName, contributionsUrl, _) =>
                client.expect[List[Contributor]](contributorsInfo(contributionsUrl, token)).map { contributors =>
                  contributors.filterNot(_.login == name).sortBy(_.contributions)(Ordering[Int].reverse)
                } map { (repoName, _) }
            }
        }
        _ <- IO(println(contributionsPerRepo.show))
      } yield ExitCode.Success
    }
  }
}
