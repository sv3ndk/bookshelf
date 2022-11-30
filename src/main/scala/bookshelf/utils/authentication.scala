package bookshelf.utils

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.http4s.Request
import org.http4s.server.AuthMiddleware

object authentication {

  sealed trait Role
  case object BookshelfUser extends Role
  case object Moderator extends Role
  case object Admin extends Role

  type UserName = String Refined NonEmpty
  case class User(username: UserName, roles: List[Role]) {
    def isAdmin = roles.contains(Admin)
  }

  // TODO: fake authentication using hard-coded user
  val hardcodedUser = User(refineMV("svend"), List(BookshelfUser, Admin))
  val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
    Kleisli(request => OptionT.some(hardcodedUser))

  val authMiddleware = AuthMiddleware(authUser)

}
