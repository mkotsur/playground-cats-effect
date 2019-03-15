package io.mkotsur.test

import cats.Show.show
import cats.data.EitherT
import cats.effect._
import cats.implicits._
import cats.{Applicative, Traverse}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.blaze._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn

object App extends IOApp {

  private def readGithubToken(envKey: String) = Option(System.getenv(envKey)) match {
    case None => IO.raiseError(new RuntimeException(s"Please set $envKey env variable"))
    case Some(t) => IO.pure(Credentials.Token(CaseInsensitiveString("token"), t))
  }

  private def repositoriesUrls(username: String, token: Credentials.Token) = Method.GET(
    Uri.uri("https://api.github.com/users/") / username / "repos",
    Authorization(token)
  )

  type ContributorsAndNextPage = (List[Contributor], Option[Uri])

  implicit val seqRepoInfosDecoder = jsonOf[IO, List[RepoInfo]]

  implicit val seqContributorDecoder = new EntityDecoder[IO, ContributorsAndNextPage] {
    private val bodyDecoder = jsonOf[IO, List[Contributor]]

    override def decode(msg: Message[IO], strict: Boolean): DecodeResult[IO, ContributorsAndNextPage] = {
      val linkHeaderParseResults = msg.headers.filter(_.name === Link.name).flatMap(_.value.split(",")).map(h => Link.parse(h.trim)).toList
      val linkHeadersEither = Traverse[List].traverse(linkHeaderParseResults)(identity).left.map(pf => new DecodeFailure {
        override def message: String = pf.getMessage()
        override def cause: Option[Throwable] = Some(pf)
        override def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]] = ???
      })

      for {
        body <- bodyDecoder.decode(msg, strict = true)
        links <- EitherT.fromEither[IO](linkHeadersEither)
      } yield {
        val uriOption = links.find(_.rel.contains("next")).map(_.uri)
        (body, uriOption)
      }
    }

    override def consumes: Set[MediaRange] = Set(MediaRange.`application/*`)
  }

  private def getContributors(uri: Uri, token: Credentials.Token)(implicit client: Client[IO]): IO[List[Contributor]] = {
    client.expect[ContributorsAndNextPage](Method.GET(uri, Authorization(token))).flatMap {
      case (contributors, None) => IO.pure(contributors)
      case (contributors, Some(nextPageUri)) => getContributors(nextPageUri, token).map { contribsNext => contribsNext ::: contributors}
    }
  }

  case class Contributor(login: String, contributions: Int)

  case class RepoInfo(full_name: String, contributors_url: String, fork: Boolean)

  implicit val contributionsShow = show[(String, List[Contributor])]({
    case (repoName, Nil) => s"$repoName ->  ~~ no contributions ~~ \n\n"
    case (repoName, contributors) => s"$repoName -> \n" + contributors.mkString("\n") + "\n\n"
  })

  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource.use { implicit client =>
      for {
        token <- readGithubToken("GH_TOKEN")
        _ <- IO(println("Please enter your Github ID ?"))
        name <- IO(StdIn.readLine())
        repoInfos <- client.expect[List[RepoInfo]](repositoriesUrls(name, token))
        _ <- IO(println(s"Found ${repoInfos.length} repos"))
        contributorsPerRepo <- repoInfos
          .filterNot(_.fork)
          .traverse {
            case RepoInfo(repoName, contributionsUrl, _) =>
              for {
                uri <- IO.fromEither(Uri.fromString(contributionsUrl))
                contributors <- getContributors(uri, token)
              } yield {
                val orderedContributors = contributors
                  .filterNot(_.login == name)
                  .sortBy(_.contributions)(Ordering[Int].reverse)
                (repoName, orderedContributors)
              }
          }
        _ <- IO(println(contributorsPerRepo.show))
      } yield ExitCode.Success
    }
}