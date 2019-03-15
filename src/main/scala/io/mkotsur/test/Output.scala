package io.mkotsur.test

import cats.Show.show
import io.mkotsur.test.App.Contributor

object Output {
  object implicits {
    implicit val contributionsShow = show[(String, List[Contributor])]({
      case (repoName, Nil)          => s"$repoName ->  ~~ no contributions ~~ \n\n"
      case (repoName, contributors) => s"$repoName -> \n" + contributors.mkString("\n") + "\n\n"
    })
  }
}
